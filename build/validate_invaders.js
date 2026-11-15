// Structural validator for the invader data added by this mod.
//
// Usage: node validate_invaders.js <profile>
//   profile = thermal | mekanism
//
// The two namespaces have genuinely different requirements (Thermal elementals are all
// projectile attackers; the Mek babies mirror their adult's melee/ranged split), so the
// namespace-specific expectations live in PROFILES rather than being hard-coded.
const fs = require('fs');
const path = require('path');
const { apothRefDir } = require('./lib_paths');

const REF = apothRefDir();

const PROFILES = {
  thermal: {
    dir: 'src/main/resources/data/apotheosis/apothic_invaders/thermal',
    modLoaded: 'thermal',
    configCond: 'apotheosis_expansion:thermal_invaders_enabled',
    entities: ['thermal:basalz', 'thermal:blizz', 'thermal:blitz'],
    // every Thermal elemental fires its own projectile and has no melee goal
    allRanged: true,
    // Thermal elementals are 0.6x1.8; boss AABB must leave room above that
    minWidth: 0.75,
    minHeight: 2.0,
    requireAllEntities: true,
    expectedCount: 7,
  },
  mekanism: {
    dir: 'src/main/resources/data/apotheosis/apothic_invaders/mekanism',
    modLoaded: 'mekanismadditions',
    configCond: 'apotheosis_expansion:mekanism_invaders_enabled',
    entities: [
      'mekanismadditions:baby_enderman',
      'mekanismadditions:baby_skeleton',
      'mekanismadditions:baby_stray',
      'mekanismadditions:baby_bogged',
      'mekanismadditions:baby_wither_skeleton',
    ],
    // babies mirror their adult: enderman melee, skeleton family ranged
    allRanged: false,
    // baby hitboxes are tiny (<=0.4 wide, <=1.53 tall); the boss AABB only has to be
    // big enough to hold the model plus the scale roll
    minWidth: 0.45,
    minHeight: 1.4,
    requireAllEntities: true,
    expectedCount: 8,
  },
  ars_nouveau: {
    dir: 'src/main/resources/data/apotheosis/apothic_invaders/ars_nouveau',
    modLoaded: 'ars_nouveau',
    configCond: 'apotheosis_expansion:ars_nouveau_invaders_enabled',
    entities: [
      'ars_nouveau:wilden_hunter',
      'ars_nouveau:wilden_stalker',
      'ars_nouveau:wilden_guardian',
    ],
    // all three are melee bruisers, so melee gear is expected
    allRanged: false,
    // hitboxes are roughly 1.0-1.2 wide and 1.0-1.2 tall
    minWidth: 1.2,
    minHeight: 1.3,
    requireAllEntities: true,
    expectedCount: 3,
  },
  vanilla_bosses: {
    dir: 'src/main/resources/data/apotheosis/apothic_invaders/vanilla_bosses',
    modLoaded: null, // no per-file mod gate; only the ancient dragon variant carries one
    configCond: 'apotheosis_expansion:vanilla_bosses_enabled',
    entities: ['minecraft:warden', 'minecraft:wither', 'minecraft:ender_dragon'],
    allRanged: false,
    minWidth: 0.9,
    minHeight: 2.9,
    requireAllEntities: true,
    expectedCount: 4,
    skipWeightedCheck: true,
    ownConditionRules: true,
    allowDuplicateEntity: true,
    extraRarities: ['ancientreforging:ancient'],
  },
  allthemodium: {
    dir: 'src/main/resources/data/apotheosis/apothic_invaders/allthemodium',
    modLoaded: 'allthemodium',
    configCond: 'apotheosis_expansion:allthemodium_invaders_enabled',
    entities: ['allthemodium:piglich'],
    allRanged: false,
    minWidth: 0.75,
    minHeight: 3.5,
    requireAllEntities: true,
    expectedCount: 1,
    // piglich lives in a non-minecraft dimension, so the generic dimension whitelist must allow it
    allowCustomDimensions: true,
  },
};

const name = process.argv[2] || 'thermal';
const P = PROFILES[name];
if (!P) {
  console.log('unknown profile ' + name + ' (use thermal|mekanism)');
  process.exit(2);
}

let fails = 0;
const ok = (cond, msg) => { if (!cond) { console.log('  FAIL ' + msg); fails++; } };

// ---------- reference data: what upstream invaders actually use ----------
const refAttrs = new Set();
const refOps = new Set();
const refEffects = new Set();
const refRarities = new Set();
const refLoot = new Set();
const refTiers = new Set();

(function walkRef(dir) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) { walkRef(p); continue; }
    const j = JSON.parse(fs.readFileSync(p, 'utf8'));
    for (const [rar, s] of Object.entries(j.stats || {})) {
      refRarities.add(rar);
      for (const m of s.attribute_modifiers || []) { refAttrs.add(m.attribute); refOps.add(m.operation); }
      for (const e of s.effects || []) refEffects.add(e.effect);
    }
    const w = j.basic_data && j.basic_data.weights;
    if (w && w.weight === undefined) for (const k of Object.keys(w)) refTiers.add(k);
    for (const t of (j.basic_data && j.basic_data.bonus_loot) || []) refLoot.add(t);
  }
})(REF);

const ATTRS = refAttrs;
const OPS = refOps;
const RARITIES = refRarities;
const TIERS = new Set(['haven', 'frontier', 'ascent', 'summit', 'pinnacle']);

function checkValue(v, where) {
  if (typeof v === 'number') return;
  ok(v && typeof v === 'object', where + ': value must be number or object');
  if (v && typeof v === 'object') {
    ok(typeof v.min === 'number' && typeof v.max === 'number', where + ': needs numeric min/max');
    ok(v.min <= v.max, where + ': min > max');
  }
}

function checkFile(file) {
  const full = path.join(P.dir, file);
  let j;
  try { j = JSON.parse(fs.readFileSync(full, 'utf8')); } catch (e) { ok(false, file + ': invalid JSON ' + e.message); return null; }
  console.log('== ' + file);

  const conds = j['neoforge:conditions'];
  if (!P.ownConditionRules) {
    ok(Array.isArray(conds) && conds.length === 3, "must carry exactly 3 conditions: mod_loaded, group, per-entity");
  }
  if (P.modLoaded) {
    ok(conds[0] && conds[0].type === 'neoforge:mod_loaded' && conds[0].modid === P.modLoaded,
      'first condition must gate on ' + P.modLoaded);
  }
  if (!P.ownConditionRules) {
    ok(conds[1] && conds[1].type === P.configCond, "second condition must be " + P.configCond);
    ok(conds[2] && conds[2].type === "apotheosis_expansion:entity_invader_enabled", "third condition must be the per-entity switch");
    ok(conds[2] && conds[2].entity === j.entity, "per-entity switch must name this file's entity");
  }

  ok(j.type === 'apotheosis:invader', 'type must be apotheosis:invader');
  ok(P.entities.includes(j.entity), 'unexpected entity ' + j.entity);

  ok(j.size && typeof j.size.width === 'number' && typeof j.size.height === 'number', 'size needs width+height');
  ok(j.size.width >= P.minWidth, 'size.width ' + j.size.width + ' < ' + P.minWidth);
  ok(j.size.height >= P.minHeight, 'size.height ' + j.size.height + ' < ' + P.minHeight);

  const b = j.basic_data;
  ok(b && typeof b === 'object', 'basic_data missing');

  // weights
  const w = b.weights;
  ok(w && typeof w === 'object', 'weights missing');
  const wkeys = Object.keys(w);
  if (wkeys.includes('weight')) {
    ok(w.weight >= 0 && w.weight <= 65536, 'weight out of codec range');
    if (w.quality !== undefined) ok(w.quality >= -16 && w.quality <= 16, 'quality out of range');
  } else {
    ok(wkeys.length > 0 && wkeys.length <= 5, 'bad tier count');
    for (const k of wkeys) {
      ok(TIERS.has(k), 'unknown tier ' + k);
      ok(w[k].weight >= 0 && w[k].weight <= 65536, k + ': weight out of range');
      if (w[k].quality !== undefined) ok(w[k].quality >= -16 && w[k].quality <= 16, k + ': quality out of range');
    }
    ok(wkeys.some(k => (w[k].weight || 0) > 0), 'all weights zero - never spawns');
  }

  // constraints
  const c = b.constraints || {};

  if (c.dimensions) for (const d of c.dimensions) {
    if (P.allowCustomDimensions) { ok(/^[a-z_]+:[a-z_/]+$/.test(d), 'malformed dimension ' + d); }
    else { ok(/^minecraft:(overworld|the_nether|the_end)$/.test(d), 'unknown dimension ' + d); }
  }
  if (c.biomes !== undefined) {
    if (typeof c.biomes === 'string') {
      ok(c.biomes.includes(':'), 'bad biome value');
    } else {
      ok(Array.isArray(c.biomes) && c.biomes.every(x => typeof x === 'string'), 'biomes must be tag string or id array');
    }
  }
  ok(c.dimensions || c.biomes, 'entry must constrain dimension or biome');

  ok(b.name === 'use_name_generation', 'name should use Apotheosis name generation');
  if (b.bonus_loot) for (const t of b.bonus_loot) ok(refLoot.has(t), 'unknown bonus loot ' + t);

  // gear
  const gs = b.valid_gear_sets;
  ok(gs && Object.keys(gs).length > 0, 'valid_gear_sets missing');
  let ranged = null;
  for (const [tier, preds] of Object.entries(gs)) {
    ok(TIERS.has(tier), 'unknown gear tier ' + tier);
    for (const p of preds) {
      ok(/^#(haven|frontier|ascent|summit|pinnacle)_(melee|ranged)$/.test(p), tier + ': bad predicate ' + p);
      const r = p.endsWith('_ranged');
      ok(ranged === null || ranged === r, 'must be consistently ranged or melee');
      ranged = r;
    }
  }
  if (P.allRanged) ok(ranged === true, 'profile requires ranged gear');

  // stats
  ok(b.stats === undefined, 'stats belong at top level');
  ok(j.stats && Object.keys(j.stats).length > 0, 'stats missing');
  for (const [rar, s] of Object.entries(j.stats)) {
    const where = file + ':' + rar;
    ok(RARITIES.has(rar) || (P.extraRarities || []).includes(rar), 'unknown rarity ' + rar);
    ok(rar !== 'apotheosis:common', 'common has no invader loot rules');
    ok(s.enchant_chance >= 0 && s.enchant_chance <= 1, where + ': enchant_chance 0..1');
    ok(Number.isInteger(s.enchantment_levels.primary) && s.enchantment_levels.primary > 0, where + ': bad primary level');
    ok(Number.isInteger(s.enchantment_levels.secondary) && s.enchantment_levels.secondary > 0, where + ': bad secondary level');
    ok(Array.isArray(s.attribute_modifiers) && s.attribute_modifiers.length > 0, where + ': no modifiers');
    const attrs = [];
    for (const m of s.attribute_modifiers) {
      attrs.push(m.attribute);
      ok(ATTRS.has(m.attribute), where + ': attribute ' + m.attribute + ' unused upstream');
      ok(OPS.has(m.operation), where + ': operation ' + m.operation + ' unused upstream');
      checkValue(m.value, where + ' ' + m.attribute);
    }
    ok(attrs.includes('minecraft:generic.max_health'), where + ': must boost max_health');
    for (const e of s.effects || []) ok(refEffects.has(e.effect), where + ': effect ' + e.effect + ' unused upstream');

    // melee vs ranged consistency
    if (ranged === true) {
      ok(!attrs.includes('minecraft:generic.attack_damage'),
        where + ': ranged entry must not buff melee attack_damage');
    }
  }

  return j;
}

const files = fs.readdirSync(P.dir).filter(f => f.endsWith('.json')).sort();
ok(files.length === P.expectedCount, 'expected ' + P.expectedCount + ' files, found ' + files.length);
const loaded = files.map(checkFile).filter(Boolean);

// entity coverage
const ents = new Set(loaded.map(j => j.entity));
if (P.requireAllEntities) {
  for (const e of P.entities) ok(ents.has(e), 'missing coverage for ' + e);
}

// no duplicate constraint for the same entity
if (!P.allowDuplicateEntity) {
  const seen = new Set();
  for (const j of loaded) {
    const key = j.entity + '|' + JSON.stringify(j.basic_data.constraints);
    ok(!seen.has(key), 'duplicate constraint for ' + j.entity);
    seen.add(key);
  }
}

console.log(fails === 0 ? '\n' + name + ': ALL CHECKS PASSED' : '\n' + name + ': ' + fails + ' CHECK(S) FAILED');
process.exit(fails === 0 ? 0 : 1);
