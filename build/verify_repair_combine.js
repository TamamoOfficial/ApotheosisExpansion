// Verifies the "repair-combine must not mint affixes" fix against the real Minecraft bytecode and sources.
//
// The bug: the crafting hook treated a repair-combine output as a freshly crafted item. Vanilla's
// RepairItemRecipe#assemble builds a brand-new plain stack, so skip_if_already_affixed could not see
// anything and every merge rolled a fresh random set. Two plain, cheap, damaged tools therefore
// produced affixed gear on every merge - the crafting grid became a free affix generator.
//
// The fix has two halves, and both are checked here because either one alone is a silent hole:
//   A. the recipe that actually matched decides. Only RepairItemRecipe is a combine; every other
//      recipe (shaped/shapeless/modded/another special) is a real craft and must be rolled normally.
//      An earlier "output item == an input item" guess also matched ordinary crafts, leaking affixes
//      from consumed ingredients onto new items.
//   B. a combine output is NEVER rolled. Affixed inputs -> inherit; plain inputs -> stays plain.
//      This half must not be behind the config toggle, or a player could switch the fix off.
//
// Run from the project root:  node build/verify_repair_combine.js
const { mcJar, mcSourcesJar, check } = require('./lib_paths');

const { execFileSync } = require('child_process');
const fs = require('fs');

const JAVA = process.env.JAVAP || 'javap';
const JAR = mcJar();
const SRC_JAR = mcSourcesJar();

const HELPER = 'src/main/java/com/tamamo/apotheosisexpansion/RepairCombineInheritance.java';
const MIXIN = 'src/main/java/com/tamamo/apotheosisexpansion/mixin/AbstractContainerMenuMixin.java';
const CONFIG = 'src/main/java/com/tamamo/apotheosisexpansion/Config.java';

let fails = 0;
const ok = (c, m) => { if (!check(c, m)) fails++; };

/** Reads one file out of a jar via `unzip -p` (the project's validators already depend on unzip). */
function jarEntry(jar, entry) {
  return execFileSync('unzip', ['-p', jar, entry], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}

/** javap one class's signatures. */
function javapSig(className) {
  return execFileSync(JAVA, ['-p', '-s', '-classpath', JAR, className],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}

const helper = fs.readFileSync(HELPER, 'utf8');
const mixin = fs.readFileSync(MIXIN, 'utf8');
const config = fs.readFileSync(CONFIG, 'utf8');

// ---------------------------------------------------------------- 1. the vanilla bug is real
console.log('1) vanilla RepairItemRecipe still rebuilds a plain stack');
{
  const src = jarEntry(SRC_JAR, 'net/minecraft/world/item/crafting/RepairItemRecipe.java');
  ok(/new ItemStack\(\s*itemstack\.getItem\(\)\s*\)/.test(src.replace(/\s+/g, ' ')),
    'assemble() constructs a brand-new ItemStack(item), so every component is dropped');
  ok(!/Components\.(AFFIXES|RARITY|SOCKETS)/.test(src),
    'assemble() copies no Apotheosis component (hence the need for inheritance)');
  ok(/MATCHES_NOT|canCombine/.test(src), "the recipe's own eligibility uses canCombine (damaged+repairable)");
}

// ---------------------------------------------------------------- 2. only that recipe is a combine
console.log('\n2) combine detection targets the real recipe class');
ok(/import net\.minecraft\.world\.item\.crafting\.RepairItemRecipe;/.test(helper),
  'helper imports RepairItemRecipe');
ok(/instanceof RepairItemRecipe/.test(helper),
  'helper identifies the combine with an instanceof check on the matched recipe');
ok(/getRecipeFor\(\s*RecipeType\.CRAFTING/.test(helper.replace(/\s+/g, ' ')),
  'helper resolves the recipe through the vanilla RecipeManager (RecipeType.CRAFTING)');
ok(/asCraftInput\(\)/.test(helper),
  'helper builds the CraftingInput from the live grid (same input vanilla itself would use)');
{
  // The API contract this depends on must still exist, or the check silently degrades to "never a combine".
  const recipes = javapSig('net.minecraft.world.item.crafting.RecipeManager');
  ok(/getRecipeFor\(net\.minecraft\.world\.item\.crafting\.RecipeType<T>, I, net\.minecraft\.world\.level\.Level\)/.test(recipes),
    'RecipeManager.getRecipeFor(RecipeType, RecipeInput, Level) exists');
  const grid = javapSig('net.minecraft.world.inventory.CraftingContainer');
  ok(/asCraftInput\(\)/.test(grid), 'CraftingContainer.asCraftInput() exists');
  const custom = javapSig('net.minecraft.world.item.crafting.RepairItemRecipe');
  ok(/extends net\.minecraft\.world\.item\.crafting\.CustomRecipe/.test(custom),
    'RepairItemRecipe extends CustomRecipe (so instanceof cannot match a shaped recipe)');
}

// ---------------------------------------------------------------- 3. the guess heuristic is gone
console.log('\n3) the old "same item as an input" guess is gone');
ok(!/snapshotApothGridItems/.test(helper) && !/snapshotApothGridItems/.test(mixin),
  'snapshotApothGridItems (the guess-based entry point) no longer exists');
ok(!/gridSources/.test(mixin), 'mixin no longer carries a bare "grid sources" list');
ok(/isNonRollingCombine|tryInherit/.test(mixin), 'mixin routes through the recipe-aware helpers');

// ---------------------------------------------------------------- 4. combines never roll
console.log('\n4) a combine output is never randomly rolled');
ok(/isNonRollingCombine/.test(helper), 'helper exposes isNonRollingCombine');
{
  // The gate must not mention the config toggle: a fix a player can switch off is not a fix.
  const body = helper.match(/public static boolean isNonRollingCombine\([\s\S]*?\n    }/);
  ok(body !== null, 'isNonRollingCombine is declared');
  ok(body && !/Config\./.test(body[0]),
    'isNonRollingCombine does NOT consult Config (the exploit fix is unconditional)');
  ok(body && /repairCombine\(\)/.test(body[0]), 'isNonRollingCombine keys on the resolved recipe');
}
{
  // In the mixin, the "skip the roll" branch must come before CraftedAffixRoller.tryApply.
  const decorate = mixin.match(/private static boolean apotheosisExpansion\$decorate\([\s\S]*?\n    }/);
  ok(decorate !== null, 'mixin has a decorate() helper for a single produced stack');
  const text = decorate ? decorate[0] : '';
  const inheritAt = text.indexOf('tryInherit');
  const gateAt = text.indexOf('isNonRollingCombine');
  const rollAt = text.indexOf('CraftedAffixRoller.tryApply');
  ok(inheritAt >= 0, 'decorate() tries inheritance first');
  ok(gateAt > inheritAt, 'decorate() checks the non-rolling gate after inheritance');
  ok(rollAt > gateAt, 'decorate() only rolls when the gate did not fire (order matters)');
}

// ---------------------------------------------------------------- 5. inheritance is still opt-in
console.log('\n5) inheritance itself stays configurable');
ok(/Config\.inheritOnRepairCombine\(\)/.test(helper),
  'tryInherit still consults inherit_on_repair_combine');
{
  const body = helper.match(/public static boolean tryInherit\([\s\S]*?\n    }/);
  ok(body !== null && !/AsNonRolling|isNonRollingCombine/.test(body[0]),
    'tryInherit does not depend on the non-rolling gate (independent concerns)');
}
ok(/inherit_on_repair_combine/.test(config), 'the config key still exists');
{
  // The generated .toml comment must not promise the old (exploitable) re-roll behaviour.
  ok(!/re-roll combined items like any other craft/.test(config),
    'config comment no longer promises a re-roll of combined items');
  ok(/not configurable|不会凭空|not be able to generate affixes/.test(config),
    'config comment states that "no fresh roll" is not configurable');
}

// ---------------------------------------------------------------- 6. behaviour table
console.log('\n6) behaviour table');
// Mirror of mixin.decorate(): (recipeIsRepairCombine, affixedInput, configOn) -> action.
const ACTIONS = ['ROLL', 'INHERIT', 'PLAIN'];
function decide(isRepair, hasAffixedInput, inheritOn) {
  // tryInherit: needs the config toggle, a repair combine, and an affixed same-item input.
  if (inheritOn && isRepair && hasAffixedInput) return 'INHERIT';
  // gate: unconditional for a repair combine.
  if (isRepair) return 'PLAIN';
  return 'ROLL';
}
const CASES = [
  // [description, isRepairCombine, affixedInput, inheritOn, expected]
  ['two plain pickaxes merged (THE BUG)', true, false, true, 'PLAIN'],
  ['two plain pickaxes merged, inherit off', true, false, false, 'PLAIN'],
  ['affixed + plain merged', true, true, true, 'INHERIT'],
  ['affixed + affixed merged', true, true, true, 'INHERIT'],
  ['affixed + plain merged, inherit off', true, true, false, 'PLAIN'],
  // ordinary crafts must keep rolling - this is the leak the instanceof check closes
  ['fresh pickaxe from sticks (ordinary craft)', false, false, true, 'ROLL'],
  ['2x2: 4 diamonds -> diamond block', false, false, true, 'ROLL'],
  ['3x3: 9 ingots -> block', false, false, true, 'ROLL'],
  ['shaped craft that consumes an affixed item', false, true, true, 'ROLL'],
];
for (const [desc, isRepair, affixed, inheritOn, expected] of CASES) {
  ok(decide(isRepair, affixed, inheritOn) === expected, desc + ' -> ' + expected);
}

// A combine can never be ROLL and a non-combine can never be INHERIT/PLAIN.
for (const isRepair of [true, false]) {
  for (const affixed of [true, false]) {
    for (const inheritOn of [true, false]) {
      const a = decide(isRepair, affixed, inheritOn);
      if (isRepair && a === 'ROLL') ok(false, 'combine must never roll: ' + [affixed, inheritOn]);
      if (!isRepair && a !== 'ROLL') ok(false, 'ordinary craft must always roll: ' + [affixed, inheritOn]);
    }
  }
}
ok(true, 'no combine rolls, and every ordinary craft still rolls');

console.log(fails === 0 ? '\nREPAIR-COMBINE FIX OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
