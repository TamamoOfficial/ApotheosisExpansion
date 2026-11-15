// Generates the Apotheosis invader data files for the Thermal Series elementals.
//
// One file per biome group, which is exactly how Apotheosis's own data is laid out
// (stray / stray_in_cold, husk / husk_in_dry, bogged / bogged_in_wet). Splitting this way keeps every
// `biomes` value a plain tag string or a plain id array, so no NeoForge holderset types are needed and
// the entries decode through the same vanilla codec path as upstream's.
const fs = require('fs');
const path = require('path');

const OUT = process.argv[2] ||
  'src/main/resources/data/apotheosis/apothic_invaders/thermal';

// ---------------------------------------------------------------- stat curves
// All three elementals are ranged attackers: Basalz/Blitz/Blizz fire projectile entities and have no
// melee goal, so the damage scaling is projectile_damage (the melee-style attack_damage multiplier
// upstream uses for zombies etc. would do nothing for them).
//
// The numbers follow the shape of upstream's ranged invaders (breeze / stray), scaled slightly down
// because Thermal's elementals already have a strong ranged attack of their own (6-8 damage per shot)
// and they fly: giving them stray's numbers as well would push them past the mythic blazes.
function rangedStats(hostileEffect) {
  return {
    'apotheosis:uncommon': {
      attribute_modifiers: [
        { attribute: 'minecraft:generic.max_health', operation: 'add_value', value: { min: 20.0, max: 40.0 } },
        { attribute: 'minecraft:generic.movement_speed', operation: 'add_multiplied_base', value: { min: 0.05, max: 0.1 } },
        { attribute: 'apothic_attributes:projectile_damage', operation: 'add_multiplied_base', value: { min: 0.2, max: 0.4 } },
        { attribute: 'minecraft:generic.knockback_resistance', operation: 'add_value', value: 0.25 },
        { attribute: 'minecraft:generic.scale', operation: 'add_multiplied_total', value: { min: -0.15, max: 0.25 } },
      ],
      effects: [{ amplifier: 1.0, effect: hostileEffect }],
      enchant_chance: 0.25,
      enchantment_levels: { primary: 20, secondary: 12 },
    },
    'apotheosis:rare': {
      attribute_modifiers: [
        { attribute: 'minecraft:generic.max_health', operation: 'add_value', value: { min: 45.0, max: 65.0 } },
        { attribute: 'minecraft:generic.movement_speed', operation: 'add_multiplied_base', value: { min: 0.15, max: 0.25 } },
        { attribute: 'apothic_attributes:projectile_damage', operation: 'add_multiplied_base', value: { min: 0.3, max: 0.55 } },
        { attribute: 'minecraft:generic.knockback_resistance', operation: 'add_value', value: 0.4 },
        { attribute: 'minecraft:generic.scale', operation: 'add_multiplied_total', value: { min: -0.15, max: 0.25 } },
      ],
      effects: [{ amplifier: 1.0, effect: hostileEffect }],
      enchant_chance: 0.35,
      enchantment_levels: { primary: 23, secondary: 15 },
    },
    'apotheosis:epic': {
      attribute_modifiers: [
        { attribute: 'minecraft:generic.max_health', operation: 'add_value', value: { min: 85.0, max: 115.0 } },
        { attribute: 'minecraft:generic.movement_speed', operation: 'add_multiplied_base', value: { min: 0.25, max: 0.4 } },
        { attribute: 'apothic_attributes:projectile_damage', operation: 'add_multiplied_base', value: { min: 0.45, max: 0.65 } },
        { attribute: 'minecraft:generic.knockback_resistance', operation: 'add_value', value: 0.5 },
        { attribute: 'minecraft:generic.armor', operation: 'add_value', value: 4.0 },
        { attribute: 'minecraft:generic.armor_toughness', operation: 'add_value', value: 10.0 },
        { attribute: 'minecraft:generic.scale', operation: 'add_multiplied_total', value: { min: -0.15, max: 0.25 } },
      ],
      effects: [{ amplifier: 1.0, effect: hostileEffect }],
      enchant_chance: 0.6,
      enchantment_levels: { primary: 35, secondary: 25 },
    },
    'apotheosis:mythic': {
      attribute_modifiers: [
        { attribute: 'minecraft:generic.max_health', operation: 'add_value', value: { min: 110.0, max: 145.0 } },
        { attribute: 'minecraft:generic.movement_speed', operation: 'add_multiplied_base', value: { min: 0.3, max: 0.65 } },
        { attribute: 'apothic_attributes:projectile_damage', operation: 'add_multiplied_base', value: { min: 0.75, max: 1.2 } },
        { attribute: 'minecraft:generic.knockback_resistance', operation: 'add_value', value: 0.8 },
        { attribute: 'minecraft:generic.armor', operation: 'add_value', value: 10.0 },
        { attribute: 'minecraft:generic.armor_toughness', operation: 'add_value', value: 20.0 },
        { attribute: 'minecraft:generic.scale', operation: 'add_multiplied_total', value: { min: -0.15, max: 0.25 } },
      ],
      effects: [{ amplifier: 1.0, effect: hostileEffect }],
      enchant_chance: 0.75,
      enchantment_levels: { primary: 35, secondary: 25 },
    },
  };
}

// Elemental hitbox is 0.6 x 1.8 (TCoreEntities). Apotheosis sizes the boss AABB a little above the
// raw hitbox to leave room for gear and the scale roll (stray: 0.6x1.99 mob -> 0.75x2.45 AABB).
const SIZE = { height: 2.25, width: 0.75 };

const RANGED_GEAR = {
  haven: ['#haven_ranged'],
  frontier: ['#frontier_ranged'],
  ascent: ['#ascent_ranged'],
  summit: ['#summit_ranged'],
  pinnacle: ['#pinnacle_ranged'],
};

function entry({ entity, biomes, dimensions, weights, hostileEffect }) {
  const constraints = {};
  if (biomes !== undefined) constraints.biomes = biomes;
  if (dimensions !== undefined) constraints.dimensions = dimensions;
  return {
    'neoforge:conditions': [
      { type: 'neoforge:mod_loaded', modid: 'thermal' },
      { type: 'apotheosis_expansion:thermal_invaders_enabled' },
    ],
    type: 'apotheosis:invader',
    basic_data: {
      bonus_loot: ['apotheosis:entity/boss_drops', 'apotheosis:entity/rare_boss_drops'],
      constraints,
      name: 'use_name_generation',
      valid_gear_sets: RANGED_GEAR,
      weights,
    },
    entity,
    size: SIZE,
    stats: rangedStats(hostileEffect),
  };
}

// TieredWeights.CODEC accepts either one Weight applied to all tiers, or a partial/full tier map.
// A partial map is what upstream uses when a boss should not appear early (husk starts at frontier).
function fromTier(firstTier, weight, quality) {
  const order = ['haven', 'frontier', 'ascent', 'summit', 'pinnacle'];
  const out = {};
  for (let i = order.indexOf(firstTier); i < order.length; i++) out[order[i]] = { quality, weight };
  return out;
}

const files = {};

// ---- Basalz: earth elemental, spawns on peaks / in caves / in the Nether's basalt deltas ----
// Fire resistance because it is a lava-dwelling earth elemental (upstream gives fire_resistance to
// nearly every boss, and Basalz is explicitly fire-adjacent).
files['basalz_peaks.json'] = entry({
  entity: 'thermal:basalz',
  biomes: 'minecraft:stony_peaks',
  dimensions: ['minecraft:overworld'],
  weights: fromTier('frontier', 50, 0.1),
  hostileEffect: 'minecraft:fire_resistance',
});
files['basalz_caves.json'] = entry({
  entity: 'thermal:basalz',
  biomes: ['minecraft:dripstone_caves', 'minecraft:lush_caves'],
  dimensions: ['minecraft:overworld'],
  weights: fromTier('frontier', 40, 0.1),
  hostileEffect: 'minecraft:fire_resistance',
});
files['basalz_nether.json'] = entry({
  entity: 'thermal:basalz',
  biomes: 'minecraft:basalt_deltas',
  dimensions: ['minecraft:the_nether'],
  weights: fromTier('frontier', 50, 0.1),
  hostileEffect: 'minecraft:fire_resistance',
});

// ---- Blizz: ice elemental, snowy biomes. Already immune to freezing; fire_resistance keeps the
// boss from being trivialised by a lava bucket, matching upstream's convention for cold mobs. ----
files['blizz.json'] = entry({
  entity: 'thermal:blizz',
  biomes: '#c:is_snowy',
  dimensions: ['minecraft:overworld'],
  weights: fromTier('frontier', 60, 0.1),
  hostileEffect: 'minecraft:fire_resistance',
});

// ---- Blitz: lightning elemental, dry / sandy / savanna biomes. ----
files['blitz_badlands.json'] = entry({
  entity: 'thermal:blitz',
  biomes: '#minecraft:is_badlands',
  dimensions: ['minecraft:overworld'],
  weights: fromTier('frontier', 50, 0.1),
  hostileEffect: 'minecraft:fire_resistance',
});
files['blitz_savanna.json'] = entry({
  entity: 'thermal:blitz',
  biomes: '#minecraft:is_savanna',
  dimensions: ['minecraft:overworld'],
  weights: fromTier('frontier', 40, 0.1),
  hostileEffect: 'minecraft:fire_resistance',
});
files['blitz_sandy.json'] = entry({
  entity: 'thermal:blitz',
  biomes: '#c:is_sandy',
  dimensions: ['minecraft:overworld'],
  weights: fromTier('frontier', 40, 0.1),
  hostileEffect: 'minecraft:fire_resistance',
});

fs.mkdirSync(OUT, { recursive: true });
// remove stale files from earlier layouts
for (const f of fs.readdirSync(OUT)) if (f.endsWith('.json') && !files[f]) fs.unlinkSync(path.join(OUT, f));

for (const [name, obj] of Object.entries(files)) {
  fs.writeFileSync(path.join(OUT, name), JSON.stringify(obj, null, 4) + '\n');
  console.log('wrote ' + name);
}
console.log('\n' + Object.keys(files).length + ' files in ' + OUT);
