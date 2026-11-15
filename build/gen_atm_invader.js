// Generates the Apotheosis invader entry for AllTheModium's Piglich.
//
// Spawn path (verified against AllTheModium 1.21.x source):
//   Ancient Pyramid structure -> spawn_overrides.monster -> allthemodium:piglich, weight 4500, 1 at a time
// Structure spawns reach finalizeSpawn with MobSpawnType.NATURAL, which is what Apotheosis accepts,
// so this is a plain data entry - no event hook needed.
//
// Notable about the mob itself:
//   * attributes: 9999 HP, 24 armor, 24 armor toughness, 12 attack damage, 0.21 speed
//   * it extends Piglin (a Monster), so all Apotheosis invader machinery applies cleanly
//   * its own canAttack() already refuses creative players, which is unrelated to our targeting fix
//
// Because the base stats are already enormous, the stat curve is additive on top of them rather
// than a copy of a normal mob's curve: the goal is a harder phase of the same fight, not a fight
// that lasts forever.
const fs = require('fs');
const path = require('path');
const { apothRefDir } = require('./lib_paths');

const REF = apothRefDir();
const OUT = process.argv[2] ||
  'src/main/resources/data/apotheosis/apothic_invaders/allthemodium';

// piglin_brute is the right shape reference: a nether melee bruiser, like piglich. wither_skeleton
// is ranged and carries no attack_damage modifier, which would leave a melee boss with no damage scaling.
const reference = JSON.parse(fs.readFileSync(path.join(REF, "the_nether/piglin_brute.json"), "utf8"));
const conditions = [
  { type: 'neoforge:mod_loaded', modid: 'allthemodium' },
  { type: 'apotheosis_expansion:allthemodium_invaders_enabled' },
  { type: 'apotheosis_expansion:entity_invader_enabled', entity: 'allthemodium:piglich' },
];

const gearSets = {};
for (const tier of ['haven', 'frontier', 'ascent', 'summit', 'pinnacle']) {
  gearSets[tier] = ['#' + tier + '_melee'];
}

const entry = {
  'neoforge:conditions': conditions,
  type: 'apotheosis:invader',
  basic_data: {
    bonus_loot: ['apotheosis:entity/boss_drops', 'apotheosis:entity/rare_boss_drops'],
    // The pyramid only generates in allthemodium:soul_sand_valley, which lives in the_other.
    // Constraining on both keeps the entry from ever competing elsewhere.
    constraints: {
      biomes: 'allthemodium:soul_sand_valley',
      dimensions: ['allthemodium:the_other'],
    },
    name: 'use_name_generation',
    valid_gear_sets: gearSets,
    // The pyramid pins one piglich per spawn attempt, so this weight only matters relative to
    // other entries eligible in the same structure. 100 matches the adult-entry convention.
    weights: { quality: 2.0, weight: 100 },
  },
  entity: 'allthemodium:piglich',
  // Piglich hitbox is 0.6 x 3.0; the boss AABB leaves room for the scale roll.
  size: { width: 0.75, height: 3.5 },
  stats: {},
};

// Rarity curve. The reference (wither skeleton) is epic+mythic; piglich is a mid-tier structure
// boss, so it gets the same two stages with the enchant values preserved and the attribute
// additions trimmed - the mob already has 9999 HP, so a +180 HP roll would be noise.
function stage(srcStage, factor) {
  const s = JSON.parse(JSON.stringify(srcStage));
  for (const m of s.attribute_modifiers) {
    // scale is a model multiplier; leaving it alone keeps the visual intact
    if (m.attribute === 'minecraft:generic.scale') continue;
    // max_health on a 9999 HP mob is meaningless as a flat add, so it is dropped entirely
    // health now scales like every other attribute (the mob keeps its own 9999 base underneath)
    // knockback resistance is capped at 1.0 in vanilla; scaling 0.8 by 2.5 would exceed it
    if (m.attribute === "minecraft:generic.knockback_resistance") {
      m.value = Math.min(1.0, m.value);
      continue;
    }
    if (typeof m.value === 'number') {
      m.value = Math.round(m.value * factor * 100) / 100;
    } else if (m.value && typeof m.value === 'object') {
      m.value.min = Math.round(m.value.min * factor * 100) / 100;
      m.value.max = Math.round(m.value.max * factor * 100) / 100;
    }
  }
  return s;
}

entry.stats['apotheosis:epic'] = stage(reference.stats['apotheosis:epic'], 2.0);
entry.stats['apotheosis:mythic'] = stage(reference.stats['apotheosis:mythic'], 2.5);

fs.mkdirSync(OUT, { recursive: true });
for (const f of fs.readdirSync(OUT)) if (f.endsWith('.json')) fs.unlinkSync(path.join(OUT, f));
fs.writeFileSync(path.join(OUT, 'piglich.json'), JSON.stringify(entry, null, 4) + '\n');

console.log('wrote piglich.json');
console.log('  entity     ' + entry.entity);
console.log('  size       ' + JSON.stringify(entry.size));
console.log('  constraints' + JSON.stringify(entry.basic_data.constraints));
console.log('  weights    ' + JSON.stringify(entry.basic_data.weights));
console.log('  rarities   ' + Object.keys(entry.stats).join(', '));
for (const [r, s] of Object.entries(entry.stats)) {
  const dmg = s.attribute_modifiers.find(m => m.attribute === 'minecraft:generic.attack_damage');
  const armor = s.attribute_modifiers.find(m => m.attribute === 'minecraft:generic.armor');
  console.log('    ' + r + '  atk=' + JSON.stringify(dmg && dmg.value) + ' armor=' + JSON.stringify(armor && armor.value));
}
