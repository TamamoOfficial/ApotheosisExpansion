// Generates Apotheosis invader data for Mekanism Additions' baby mobs.
//
// Design decisions (user-confirmed):
//   Q1 = A  : cover every biome the adult can spawn in -> constrain by dimension only
//   Q1-supp : baby weight = 1/3 of the corresponding adult invader's weight, per tier
//   Q1-2    : creeper is NOT done (Apotheosis has no adult creeper invader to derive from)
//   Q2      : wither skeleton deferred (structure spawns never reach Apotheosis' NATURAL gate)
//   Q3      : skeleton family -> ranged gear + projectile_damage; enderman/bogged -> melee
//   Q4      : keep roughly adult-sized boss AABB; the baby model is tiny, a proportional
//             AABB would be far smaller than the boss and look broken
//
// Because the baby's own attributes are already nerfed by Additions (-50% health,
// -50% speed, -75% attack), the stat curves are copied verbatim from the adult entry:
// Apotheosis applies these as additions on top, so the resulting boss lands slightly below
// its adult counterpart, which is the intent.
const fs = require('fs');
const path = require('path');
const { apothRefDir } = require('./lib_paths');

const REF = apothRefDir();
const OUT = process.argv[2] ||
  'src/main/resources/data/apotheosis/apothic_invaders/mekanism';

const read = p => JSON.parse(fs.readFileSync(path.join(REF, p), 'utf8'));

// adult sources
const adultOverworldEnderman = read('overworld/enderman.json');   // ranged? no: melee, asc+
const adultEndEnderman = read('the_end/enderman.json');           // melee, all tiers
const adultSkeleton = read('overworld/skeleton.json');            // ranged
const adultStray = read('overworld/stray.json');                  // ranged, frontier+
const adultStrayCold = read('overworld/stray_in_cold.json');      // ranged, all tiers, cold biomes
const adultBogged = read('overworld/bogged.json');                // ranged, frontier+
const adultBoggedWet = read('overworld/bogged_in_wet.json');      // ranged, all tiers, wet biomes

const addonConditions = [
  { type: 'neoforge:mod_loaded', modid: 'mekanismadditions' },
  { type: 'apotheosis_expansion:mekanism_invaders_enabled' },
];

/** Copy the adult's whole stat table. Babies inherit the adult's curve unchanged. */
function statsOf(adult) {
  return JSON.parse(JSON.stringify(adult.stats));
}

/** Exactly one third of each adult weight, rounded, floored at 1. */
function thirdOf(weights) {
  if (weights.weight !== undefined) {
    return { quality: weights.quality, weight: Math.max(1, Math.round(weights.weight / 3)) };
  }
  const out = {};
  for (const [tier, v] of Object.entries(weights)) {
    out[tier] = { quality: v.quality, weight: Math.max(1, Math.round(v.weight / 3)) };
  }
  return out;
}

/** Adult's gear pool, unchanged (ranged stays ranged, melee stays melee). */
function gearOf(adult) {
  return JSON.parse(JSON.stringify(adult.basic_data.valid_gear_sets));
}

// Baby hitboxes are small (skeleton-family 0.3375 x 1.119, enderman 0.315 x 1.5225).
// Use the adult boss AABB's width/2-ish so the boss body still fills a sensible volume:
// a boss AABB must contain the mob hitbox plus room for the scale roll.
// Skeleton adult boss is 0.75 x 2.45; babies use 0.5 x 1.5.
// Enderman adult boss is 0.75 x 3.7; babies use 0.5 x 1.9 (baby enderman is 1.52 tall).
const SIZE = {
  skeleton: { width: 0.5, height: 1.5 },
  enderman: { width: 0.5, height: 1.9 },
};

function entry({ entity, adult, size, weights, dimensions, biomes }) {
  const constraints = {};
  if (biomes !== undefined) constraints.biomes = biomes;
  if (dimensions !== undefined) constraints.dimensions = dimensions;
  return {
    'neoforge:conditions': addonConditions,
    type: 'apotheosis:invader',
    basic_data: {
      bonus_loot: JSON.parse(JSON.stringify(adult.basic_data.bonus_loot)),
      constraints,
      name: 'use_name_generation',
      valid_gear_sets: gearOf(adult),
      weights,
    },
    entity,
    size,
    stats: statsOf(adult),
  };
}

const files = {};

// ---- baby enderman: overworld (melee, ascent+) ----
files['baby_enderman.json'] = entry({
  entity: 'mekanismadditions:baby_enderman',
  adult: adultOverworldEnderman,
  size: SIZE.enderman,
  weights: thirdOf(adultOverworldEnderman.basic_data.weights),
  dimensions: ['minecraft:overworld'],
});

// ---- baby enderman: the end (melee, all tiers) ----
files['baby_enderman_end.json'] = entry({
  entity: 'mekanismadditions:baby_enderman',
  adult: adultEndEnderman,
  size: SIZE.enderman,
  weights: thirdOf(adultEndEnderman.basic_data.weights),
  dimensions: ['minecraft:the_end'],
});

// ---- baby skeleton: overworld (ranged, all tiers) ----
files['baby_skeleton.json'] = entry({
  entity: 'mekanismadditions:baby_skeleton',
  adult: adultSkeleton,
  size: SIZE.skeleton,
  weights: thirdOf(adultSkeleton.basic_data.weights),
  dimensions: ['minecraft:overworld'],
});

// ---- baby stray: overworld (ranged, frontier+) ----
files['baby_stray.json'] = entry({
  entity: 'mekanismadditions:baby_stray',
  adult: adultStray,
  size: SIZE.skeleton,
  weights: thirdOf(adultStray.basic_data.weights),
  dimensions: ['minecraft:overworld'],
});

// ---- baby stray: cold biomes (ranged, all tiers) ----
files['baby_stray_cold.json'] = entry({
  entity: 'mekanismadditions:baby_stray',
  adult: adultStrayCold,
  size: SIZE.skeleton,
  weights: thirdOf(adultStrayCold.basic_data.weights),
  biomes: '#c:is_cold/overworld',
  dimensions: ['minecraft:overworld'],
});

// ---- baby bogged: overworld (ranged, frontier+) ----
files['baby_bogged.json'] = entry({
  entity: 'mekanismadditions:baby_bogged',
  adult: adultBogged,
  size: SIZE.skeleton,
  weights: thirdOf(adultBogged.basic_data.weights),
  dimensions: ['minecraft:overworld'],
});

// ---- baby bogged: wet biomes (ranged, all tiers) ----
files['baby_bogged_wet.json'] = entry({
  entity: 'mekanismadditions:baby_bogged',
  adult: adultBoggedWet,
  size: SIZE.skeleton,
  weights: thirdOf(adultBoggedWet.basic_data.weights),
  biomes: '#c:is_wet/overworld',
  dimensions: ['minecraft:overworld'],
});

fs.mkdirSync(OUT, { recursive: true });
for (const f of fs.readdirSync(OUT)) if (f.endsWith('.json') && !files[f]) fs.unlinkSync(path.join(OUT, f));

for (const [name, obj] of Object.entries(files)) {
  fs.writeFileSync(path.join(OUT, name), JSON.stringify(obj, null, 4) + '\n');
  const w = obj.basic_data.weights;
  const wdesc = w.weight !== undefined ? String(w.weight) : Object.entries(w).map(([k, v]) => k + ':' + v.weight).join(' ');
  console.log(name.padEnd(26) + obj.entity.padEnd(42) + 'w=' + wdesc);
}
console.log('\n' + Object.keys(files).length + ' files -> ' + OUT);
