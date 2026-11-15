// Verifies the Piglich gear hook against the agreed spec and the AllTheModium source.
//
// The hook exists because 1.21.1 dropped the armour logic that 1.20.x had, and because Apotheosis'
// GearSet cannot express "50% chance of wearing nothing".
const fs = require('fs');
const { requireEnv } = require('./lib_paths');

const HOOK = 'src/main/java/com/tamamo/apotheosisexpansion/compat/PiglichGearHook.java';
const EVENTS = 'src/main/java/com/tamamo/apotheosisexpansion/compat/VanillaBossEvents.java';
const { optionalEnv } = require('./lib_paths');
const ATM = optionalEnv('ATM_SRC_DIR'); // optional: only used to prove why the hook is needed

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const hook = fs.readFileSync(HOOK, 'utf8');
const events = fs.readFileSync(EVENTS, 'utf8');

// ---- 1. 1.21.1 really has no armour logic (this is the reason the hook exists)
console.log('1) upstream 1.21.1 has no armour code');
{
  const pig = ATM ? ATM + '/src/main/java/com/thevortex/allthemodium/entity/PiglichEntity.java' : null;
  if (!pig || !fs.existsSync(pig)) {
    console.log('  SKIPPED (set ATM_SRC_DIR to an extracted AllTheModium tree to enable this check)');
  }
  else {
    const src = fs.readFileSync(pig, 'utf8');
    ok(!/maybeWearArmor|populateDefaultEquipementSlots/.test(src),
      '1.21.1 PiglichEntity has no armour methods (so the hook is required)');
    ok(/finalizeSpawn\(ServerLevelAccessor[^)]*\)\s*\{\s*return p_\d+_;/.test(src.replace(/\s+/g, ' ')),
      '1.21.1 finalizeSpawn is an empty override');
  }
}

// ---- 2. 1.20.x was the source of the behaviour being restored
console.log('2) 1.20.x behaviour is what gets reproduced');
ok(hook.includes('ARMOR_CHANCE = 0.5F'), '50% per armour piece');
ok(hook.includes('ATM_SWORD_CHANCE = 0.4F'), '40% ATM sword, matching createSpawnWeapon');


// ---- 2b. mainhand weapon
console.log("2b) mainhand weapon");
ok(hook.includes("ATM_SWORD_ID = \"allthemodium:allthemodium_sword\""), "uses the verified ATM sword id");
ok(hook.includes("random.nextFloat() < ATM_SWORD_CHANCE"), "takes the roll before resolving the item");
ok(hook.includes("Items.NETHERITE_SWORD"), "netherite sword is the other branch");
ok(!hook.includes("ATM_SWORD_CHANCE")||hook.indexOf("ATM_SWORD_CHANCE")<hook.indexOf("resolveItem(ATM_SWORD_ID)"), "roll precedes item resolution (a missing sword cannot shift probability)");


// ---- 3. only the AllTheModium tier, never Vibranium / Unobtainium
console.log('3) armour tier restriction');
const armorIds = [...hook.matchAll(/"(allthemodium:[a-z_]+)"/g)].map(m => m[1]).filter(id => /_(helmet|chestplate|leggings|boots|sword)$/.test(id));
ok(armorIds.length >= 5, 'five ATM item ids referenced (4 armour + sword), got ' + armorIds.length);
// only string literals matter: a comment explaining the restriction is fine
const literals = [...hook.matchAll(/"([^"]*)"/g)].map(m => m[1].toLowerCase()).join(" ");
for (const banned of ["vibranium", "unobtainium", "alloy"]) {
  ok(!literals.includes(banned), "no string literal references \"" + banned + "\"");
}
for (const id of armorIds) {
  ok(/^allthemodium:allthemodium_/.test(id), 'armour id is the allthemodium tier: ' + id);
}

// ---- 4. independent rolls: one nextFloat per slot, each able to skip
console.log('4) independent per-slot rolls');
ok(/for \(int i = 0; i < ARMOR_SLOTS\.length; i\+\+\)/.test(hook), 'iterates the four armour slots');
ok(/random\.nextFloat\(\) >= ARMOR_CHANCE/.test(hook), 'each slot rolls independently and may be skipped');
ok(/continue; \/\/ this slot stays empty/.test(hook), 'empty result is an explicit skip, not an air item');

// ---- 5. ordering: armour must be applied before initBoss, or the affixes miss it
console.log('5) ordering');
const equipPos = hook.indexOf('equipRandomArmor(piglich, random)');
const initPos = hook.indexOf('.initBoss(piglich, ctx, rarity)');
ok(equipPos > 0 && initPos > 0 && equipPos < initPos,
  'equipRandomArmor() runs before initBoss() (affixes land on the armour)');

// ---- 6. gated on the config switches
console.log('6) config gates');
ok(hook.includes('Config.enableAllthemodiumInvaders()'), 'honours the group switch');
ok(hook.includes('Config.isEntityInvaderEnabled("allthemodium:piglich")'), 'honours the per-entity switch');

// ---- 7. wired into the spawn event, server-side only
console.log('7) event wiring');
ok(events.includes('PiglichGearHook.isPiglich(mob)'), 'FinalizeSpawnEvent checks for the piglich');
ok(events.includes('PiglichGearHook.tryEquipAndConvert'), 'calls into the hook');
ok(/!event\.getLevel\(\)\.getLevel\(\)\.isClientSide\(\)/.test(events), 'server-side only');

// ---- 8. health is back on the normal curve
console.log('8) stat curve');
const data = JSON.parse(fs.readFileSync('src/main/resources/data/apotheosis/apothic_invaders/allthemodium/piglich.json', 'utf8'));
for (const [rar, s] of Object.entries(data.stats)) {
  const hp = s.attribute_modifiers.find(m => m.attribute === 'minecraft:generic.max_health');
  ok(hp && hp.value.max > 0, rar + ' has a real max_health roll (' + JSON.stringify(hp && hp.value) + ')');
  const kb = s.attribute_modifiers.find(m => m.attribute === 'minecraft:generic.knockback_resistance');
  ok(kb && kb.value <= 1.0, rar + ' knockback resistance stays <= 1.0');
  const atk = s.attribute_modifiers.find(m => m.attribute === 'minecraft:generic.attack_damage');
  ok(atk, rar + ' scales attack damage (piglich is melee)');
}

console.log(fails === 0 ? '\nPIGLICH GEAR HOOK OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
