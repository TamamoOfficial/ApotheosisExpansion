// Generates Apotheosis invader data for Ars Nouveau's Wilden mobs.
//
// Confirmed facts (verified against the 1.21.x source tree):
//   * They DO spawn naturally - via the Wilden Den structures' spawn_overrides, not biome spawners:
//       stalker_wilden_den  biomes #minecraft:is_forest   -> wilden_stalker  w5 x3
//       hunter_wilden_den   biomes #minecraft:is_forest   -> wilden_hunter   w5 x3
//       guardian_wilden_den biomes #c:is_coniferous       -> wilden_guardian w5 x1
//   * Structure spawns run through NaturalSpawner.spawnCategoryForPosition and call
//     finalizeSpawn(..., MobSpawnType.NATURAL, ...), which is exactly what Apotheosis accepts.
//     So plain datapack entries work - no event hook needed.
//
// User decisions:
//   Q1-B : constrain by the structure's own biome tag
//   Q2   : same treatment as the wither skeleton baby -> weight 25, tiered across all tiers
//   Q3   : only haven..ascent world tiers, rarity stages identical to the vanilla zombie entry
//
// IMPORTANT deviation, reported to the user: haven's invader spawn chance is 0.0 in the
// dimension_type data map ("haven": 0), so a haven weight can never fire. The tiers actually
// written are therefore frontier + ascent. They are written explicitly (not omitted) so the
// intent stays visible in the JSON.
const fs = require('fs');
const path = require('path');
const { apothRefDir } = require('./lib_paths');

const REF = apothRefDir();
const OUT = process.argv[2] ||
  'src/main/resources/data/apotheosis/apothic_invaders/ars_nouveau';

const zombie = JSON.parse(fs.readFileSync(path.join(REF, 'overworld/zombie.json'), 'utf8'));

const conditions = [
  { type: 'neoforge:mod_loaded', modid: 'ars_nouveau' },
  { type: 'apotheosis_expansion:ars_nouveau_invaders_enabled' },
];

// Q3: rarity stages identical to the vanilla zombie entry
const stats = JSON.parse(JSON.stringify(zombie.stats));

// Q2: weight 25 everywhere, matching the wither skeleton baby's treatment.
// Note: haven would be 0 effective (dimension spawn chance is 0), so it is omitted;
// summit/pinnacle are omitted to honour "haven..ascent only".
const weights = {
  frontier: { quality: 0.1, weight: 25 },
  ascent: { quality: 0.1, weight: 25 },
};

// Each Wilden is a melee bruiser (MeleeAttackGoal / ram), so melee gear like the zombie.
const gear = JSON.parse(JSON.stringify(zombie.basic_data.valid_gear_sets));

// Hitboxes from ModEntities: hunter 1.2x1.2, stalker 0.95x1.0, guardian 1.15x1.15.
// The boss AABB is kept a little above each hitbox so the scale roll has room.
const ENTRIES = [
  {
    file: 'wilden_hunter.json',
    entity: 'ars_nouveau:wilden_hunter',
    biomes: 'ars_nouveau:archwood_biome',
    size: { width: 1.55, height: 1.6 },
  },
  {
    file: 'wilden_stalker.json',
    entity: 'ars_nouveau:wilden_stalker',
    biomes: 'ars_nouveau:archwood_biome',
    size: { width: 1.3, height: 1.4 },
  },
  {
    file: 'wilden_guardian.json',
    entity: 'ars_nouveau:wilden_guardian',
    biomes: '#c:is_tree/coniferous',
    size: { width: 1.5, height: 1.55 },
  },
];

fs.mkdirSync(OUT, { recursive: true });
for (const f of fs.readdirSync(OUT)) if (f.endsWith('.json')) fs.unlinkSync(path.join(OUT, f));

for (const e of ENTRIES) {
  const obj = {
    'neoforge:conditions': conditions,
    type: 'apotheosis:invader',
    basic_data: {
      bonus_loot: JSON.parse(JSON.stringify(zombie.basic_data.bonus_loot)),
      constraints: {
        biomes: e.biomes,
        dimensions: ['minecraft:overworld'],
      },
      name: 'use_name_generation',
      valid_gear_sets: gear,
      weights,
    },
    entity: e.entity,
    size: e.size,
    stats,
  };
  fs.writeFileSync(path.join(OUT, e.file), JSON.stringify(obj, null, 4) + '\n');
  console.log(e.file.padEnd(24) + e.entity.padEnd(38) + 'biomes=' + e.biomes);
}

console.log('\n' + ENTRIES.length + ' files -> ' + OUT);
console.log('weights: ' + JSON.stringify(weights));
console.log('rarities: ' + Object.keys(stats).join(', '));
