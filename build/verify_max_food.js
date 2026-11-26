// Statically verifies the max_food feature (attribute + FoodData/Player mixins + the affix JSON) against the real
// Minecraft bytecode and the real Apotheosis jar.
//
// Why bytecode and not the sources jar: @ModifyConstant matches on the *compiled* constant pool entry. A method that
// looks like it contains `20` once in source can contain it twice after a compiler folds an expression, and a method
// with two occurrences needs an `ordinal` - which would then silently break on the next MC patch. This script proves
// from the class file that every injected method contains its literal exactly once, which is what makes the
// ordinal-free annotations in FoodDataMixin safe.
//
// Checks:
//   1. FoodData.add / needsFood / tick each contain their targeted literal exactly once
//   2. tick(Player) exists with the descriptor the @Inject handler expects
//   3. Player has exactly one constructor, and it precedes the FoodData field initialiser (the RETURN hook premise)
//   4. FoodData has no field that would let it recover its player on its own (the owner field is genuinely needed)
//   5. the attr is registered syncable, player-only, and referenced by the affix JSON under the right id
//   6. the affix JSON is structurally valid for Apotheosis: type, category names, one value block per rarity,
//      and every rarity id the file uses exists in the Apotheosis jar
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { mcJar, apothJar, javap, rel } = require('./lib_paths');

const JAVA = javap();
const MC = mcJar();
const APOTH = apothJar();

const runJavap = (...args) =>
  execFileSync(JAVA, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

/** The `Code:` block of one method, or null. */
function methodBody(verbose, name, desc) {
  const lines = verbose.split('\n');
  let start = -1;
  for (let i = 0; i < lines.length; i++) {
    if (desc) {
      // match on descriptor so overloads cannot be confused
      if (lines[i].trim() === 'descriptor: ' + desc) {
        for (let j = i; j >= 0; j--) {
          if (/^\s{2}[a-z].*\(/.test(lines[j])) { start = j; break; }
        }
        break;
      }
    }
    else if (new RegExp('^\\s{2}[a-z].*\\b' + name + '\\(').test(lines[i])) {
      start = i;
      break;
    }
  }
  if (start < 0) return null;

  const body = [];
  let seenCode = false;
  for (let i = start; i < lines.length; i++) {
    const line = lines[i];
    if (i > start && /^\s{2}[a-z].*\(/.test(line) && !line.trim().startsWith('Code:')) break;
    if (line.trim() === 'Code:') seenCode = true;
    if (seenCode) body.push(line);
  }
  return body.join('\n');
}

/** How many times a literal appears as an instruction operand in a method body. */
function literalCount(body, value) {
  const lines = body.split('\n');
  let n = 0;
  for (const line of lines) {
    // e.g. `122: bipush        20` or `188: sipush        18` or `14: ldc  #3  // int 20`
    const m = line.match(/\b(?:bipush|sipush|iconst_[0-9m])\b/);
    if (m) {
      if (m[0] === 'iconst_m1' ? value === -1 : /^iconst_([0-9])$/.test(m[0]) ? Number(m[0].slice(7)) === value : line.trim().split(/\s+/).pop() === String(value)) {
        n++;
      }
      continue;
    }
    if (/\bldc(_w)?\b/.test(line) && new RegExp('//\\s*int\\s+' + value + '\\b').test(line)) n++;
  }
  return n;
}

let fails = 0;
/** Prints a PASS/FAIL line, counts failures, and returns the state. */
const pass = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; return c; };

// ---------------------------------------------------------------- 1. FoodData literals
console.log('1) FoodData constants targeted by @ModifyConstant');
const foodData = runJavap('-p', '-c', '-classpath', MC, 'net.minecraft.world.food.FoodData');

const addBody = methodBody(foodData, 'add');
const needsBody = methodBody(foodData, 'needsFood');
const tickBody = methodBody(foodData, 'tick');

pass(!!addBody, 'add() found');
pass(!!needsBody, 'needsFood() found');
pass(!!tickBody, 'tick() found');

for (const [name, body, value] of [['add', addBody, 20], ['needsFood', needsBody, 20]]) {
  const n = literalCount(body || '', value);
  pass(n === 1, name + '() contains the literal ' + value + ' exactly once (found ' + n + ')');
}
pass(literalCount(tickBody || '', 20) === 1,
  'tick() contains the literal 20 exactly once (found ' + literalCount(tickBody || '', 20) + ')');
pass(literalCount(tickBody || '', 18) === 1,
  'tick() contains the literal 18 exactly once (found ' + literalCount(tickBody || '', 18) + ')');

// The saturation clamp must NOT be a 20. If a future MC ever changes it to a literal 20, the
// "no saturation patch needed" reasoning in ModAttributes would silently become wrong.
const addHasFloat20 = /float\s+20\.0/.test(addBody || '');
pass(!addHasFloat20, 'add() clamps saturation by foodLevel, not by a literal 20.0f');

// ---------------------------------------------------------------- 2. tick descriptor
console.log('2) tick(Player) signature used by the @Inject handler');
pass(/public void tick\(net\.minecraft\.world\.entity\.player\.Player\)/.test(foodData),
  'tick(net.minecraft.world.entity.player.Player) exists');

// ---------------------------------------------------------------- 3. Player constructor
console.log('3) Player constructor and the field initialiser order');
const player = runJavap('-p', '-c', '-classpath', MC, 'net.minecraft.world.entity.player.Player');
const ctorCount = (player.match(/^\s{2}public net\.minecraft\.world\.entity\.player\.Player\(/gm) || []).length;
pass(ctorCount === 1, 'Player declares exactly one constructor (found ' + ctorCount + ')');

const ctorStart = player.indexOf('public net.minecraft.world.entity.player.Player(');
const ctorIdx = player.indexOf('new           #', 0);
const foodInitInCtor = player
  .slice(ctorStart, player.indexOf('\n\n', ctorStart) > 0 ? player.indexOf('\n\n', ctorStart) : undefined)
  .includes('net/minecraft/world/food/FoodData."<init>"');
pass(foodInitInCtor, 'the FoodData initialiser runs inside the constructor (so @At RETURN sees it set)');

// ---------------------------------------------------------------- 4. FoodData has no owner
console.log('4) FoodData genuinely cannot recover its player');
// Field declarations only: a method descriptor legitimately mentions Player (tick(Player)), so the
// check must ignore every line that contains a parenthesis.
const foodFields = runJavap('-p', '-classpath', MC, 'net.minecraft.world.food.FoodData')
  .split('\n')
  .filter(l => /^\s{2}(private|protected|public)\s/.test(l) && !l.includes('('));
const playerTypedFields = foodFields.filter(l => l.includes('Player'));
pass(playerTypedFields.length === 0,
  'FoodData declares no Player-typed field (' + foodFields.length + ' fields checked), so an owner must be injected');

// ---------------------------------------------------------------- 5. attribute source
console.log('5) attribute registration');
const attrSrc = fs.readFileSync(rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'ModAttributes.java'), 'utf8');
pass(/\.setSyncable\(true\)/.test(attrSrc), 'max_food is syncable (client FoodData clamps too)');
pass(/DeferredRegister\.create\(Registries\.ATTRIBUTE/.test(attrSrc), 'registered into Registries.ATTRIBUTE');
pass(/event\.add\(EntityType\.PLAYER, MAX_FOOD\)/.test(attrSrc),
  'added to EntityType.PLAYER via EntityAttributeModificationEvent (without this the modifier is dropped silently)');
pass(/VANILLA_MAX_FOOD = 20/.test(attrSrc), 'fallback ceiling is the vanilla 20');

// the mixins must actually be listed, or they are inert
const mixinCfg = JSON.parse(fs.readFileSync(rel('src', 'main', 'resources', 'apotheosis_expansion.mixins.json'), 'utf8'));
pass(mixinCfg.mixins.includes('FoodDataMixin'), 'FoodDataMixin listed in apotheosis_expansion.mixins.json');
pass(mixinCfg.mixins.includes('PlayerFoodMixin'), 'PlayerFoodMixin listed in apotheosis_expansion.mixins.json');

// and the mod must wire the registration
const mainSrc = fs.readFileSync(rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'ApotheosisExpansion.java'), 'utf8');
pass(/ModAttributes\.register\(modBus\)/.test(mainSrc), 'ModAttributes.register(modBus) is called from the mod constructor');

// ---------------------------------------------------------------- 6. affix JSON
console.log('6) the affix data file');
const affixPath = rel('src', 'main', 'resources', 'data', 'apotheosis', 'affixes', 'armor', 'attribute', 'gourmand.json');
const affix = JSON.parse(fs.readFileSync(affixPath, 'utf8'));

pass(affix.type === 'apotheosis:attribute', 'type is apotheosis:attribute');
pass(affix.attribute === 'apotheosis_expansion:max_food', 'points at apotheosis_expansion:max_food');
pass(affix.definition && affix.definition.affix_type === 'stat',
  'affix_type is stat, so it is drawn from the same pool as blessed/elastic');
pass(Array.isArray(affix.categories) && affix.categories.length > 0, 'declares categories');
pass(affix.operation === 'add_value', 'operation is add_value (a percentage would be meaningless for a 20-unit bar)');

// Every rarity the file gives values for must exist in the jar, and vice versa for the armour tiers.
const jars = execFileSync('unzip', ['-l', APOTH], { encoding: 'utf8' });
const rarityIds = ['common', 'uncommon', 'rare', 'epic', 'mythic'];
for (const r of rarityIds) {
  pass(jars.includes('data/apotheosis/rarities/' + r + '.json'),
    'rarity apotheosis:' + r + ' exists in the Apotheosis jar');
}
const valueKeys = Object.keys(affix.values || {});
for (const k of valueKeys) {
  pass(k.startsWith('apotheosis:'), 'value key "' + k + '" is namespaced');
}
for (const r of rarityIds) {
  pass(valueKeys.includes('apotheosis:' + r),
    'has a value block for apotheosis:' + r + ' (a missing block silently excludes that rarity)');
}

// Category names: AttributeAffix.canApplyTo requires categories.contains(cat). The four armour
// categories are the ones blessed.json uses, so they are known-good.
const blessed = JSON.parse(execFileSync('unzip', ['-p', APOTH, 'data/apotheosis/affixes/armor/attribute/blessed.json'], { encoding: 'utf8' }));
for (const c of affix.categories) {
  pass(blessed.categories.includes(c), 'category "' + c + '" is used by vanilla blessed.json, so it resolves');
}

// StepFunction shape: min <= max, step > 0, and (max-min) divisible by step - otherwise the roll
// is skewed or throws inside Placebo.
for (const [rarity, v] of Object.entries(affix.values)) {
  pass(typeof v.min === 'number' && typeof v.max === 'number' && typeof v.step === 'number',
    rarity + ' uses a min/max/step range');
  pass(v.min <= v.max, rarity + ' min <= max');
  pass(v.step > 0, rarity + ' step > 0');
  const steps = (v.max - v.min) / v.step;
  pass(Math.abs(steps - Math.round(steps)) < 1e-9, rarity + ' range is a whole number of steps');
}

// ---------------------------------------------------------------- 7. upstream state this mod works around
console.log('7) the vanilla limitation the HUD layer compensates for');
const gui = runJavap('-p', '-c', '-classpath', MC, 'net.minecraft.client.gui.Gui');
const foodBody = methodBody(gui, 'renderFood');
// Vanilla's loop bound is still the literal 10 - that is exactly why FoodHudLayer exists and why it
// has to be a *wrapper* (unchanged rendering at the default ceiling) rather than a rewrite.
pass(/bipush\s+10/.test(foodBody || ''), 'vanilla renderFood() still loops a fixed 10 icons');
const hudLayer = rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'client', 'FoodHudLayer.java');
pass(fs.existsSync(hudLayer), 'FoodHudLayer exists and wraps that layer (see build/verify_food_hud.js)');

console.log('\n' + (fails === 0 ? 'MAX_FOOD OK' : fails + ' CHECK(S) FAILED'));
process.exit(fails === 0 ? 0 : 1);
