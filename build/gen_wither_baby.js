// Generates the wither skeleton baby invader entry.
//
// User-confirmed decisions:
//   - reading B: baby weight 25 while the adult's 100 is left untouched, so a baby is exactly
//     1/4 as likely as the adult (do NOT modify Apotheosis' own wither_skeleton.json)
//   - weights are tiered normally (ascent / summit / pinnacle)
//   - ONLY the epic rarity tier is provided
//   - spawn conditions identical to the baby's own: the_nether dimension, no biome constraint,
//     because the fortress replaces the biome's spawn table wholesale (isInNetherFortressBounds)
//
// Why the nether works at all: the fortress roll goes through the runtime natural-spawn path
// (NaturalSpawner.spawnCategoryForPosition) which calls finalizeSpawn(..., MobSpawnType.NATURAL, ...),
// and Apotheosis accepts NATURAL. There is no STRUCTURE gate to defeat.
const fs = require('fs');
const path = require('path');
const { apothRefDir } = require('./lib_paths');

const REF = apothRefDir();
const OUT = process.argv[2] ||
  'src/main/resources/data/apotheosis/apothic_invaders/mekanism';

const adult = JSON.parse(fs.readFileSync(path.join(REF, 'the_nether/wither_skeleton.json'), 'utf8'));

// The adult ships only epic + mythic; we take the epic stage alone, as instructed.
if (!adult.stats['apotheosis:epic']) {
  throw new Error('adult wither_skeleton has no epic stage - source assumption broken');
}

const entry = {
  'neoforge:conditions': [
    { type: 'neoforge:mod_loaded', modid: 'mekanismadditions' },
    { type: 'apotheosis_expansion:mekanism_invaders_enabled' },
  ],
  type: 'apotheosis:invader',
  basic_data: {
    bonus_loot: JSON.parse(JSON.stringify(adult.basic_data.bonus_loot)),
    // dimension only: fortresses replace the whole biome spawn table, so a biome constraint
    // would wrongly exclude most fortress locations
    constraints: { dimensions: ['minecraft:the_nether'] },
    name: 'use_name_generation',
    valid_gear_sets: JSON.parse(JSON.stringify(adult.basic_data.valid_gear_sets)),
    // weight 25 = 1/4 of the adult's 100, so a baby is a quarter as likely to be picked
    weights: {
      ascent: { quality: 1.5, weight: 25 },
      summit: { quality: 1.5, weight: 25 },
      pinnacle: { quality: 1.5, weight: 25 },
    },
  },
  entity: 'mekanismadditions:baby_wither_skeleton',
  // baby wither skeleton hitbox: 0.7 x 2.4 scaled by 0.5625 -> 0.394 x 1.35
  // boss AABB stays modest but must comfortably contain the model
  size: { width: 0.75, height: 2.0 },
  stats: { 'apotheosis:epic': JSON.parse(JSON.stringify(adult.stats['apotheosis:epic'])) },
};

const name = 'baby_wither_skeleton.json';
fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, name), JSON.stringify(entry, null, 4) + '\n');
console.log('wrote ' + name);
console.log('  entity   ' + entry.entity);
console.log('  weights  ' + JSON.stringify(entry.basic_data.weights));
console.log('  rarities ' + Object.keys(entry.stats));
console.log('  size     ' + JSON.stringify(entry.size));
console.log('  constraints ' + JSON.stringify(entry.basic_data.constraints));
