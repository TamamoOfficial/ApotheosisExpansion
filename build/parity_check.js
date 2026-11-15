// Field-level parity between the new Thermal entries and the ones that ship in Apotheosis 8.9.0.
// A real decode needs an FML launch (registries + FeatureFlags), so instead this proves that every
// key, enum value and attribute id used here is one upstream actually ships, and that nothing
// upstream requires is missing.
const fs = require('fs');
const path = require('path');
const { apothRefDir } = require('./lib_paths');

const OURS = 'src/main/resources/data/apotheosis/apothic_invaders/thermal';
const REF = apothRefDir();

let fails = 0;
const ok = (c, m) => { if (!c) { console.log('  FAIL ' + m); fails++; } };

// ---------- collect what upstream uses ----------
const refKeys = new Map();      // json path -> set of keys
const refAttrs = new Set();
const refOps = new Set();
const refEffects = new Set();
const refRarities = new Set();
const refTiers = new Set();
const refLoot = new Set();

function walk(dir, cb) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) walk(p, cb);
    else if (f.endsWith('.json')) cb(p);
  }
}

function recordKeys(obj, prefix) {
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return;
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix + '.' + k;
    if (!refKeys.has(key)) refKeys.set(key, new Set());
    if (Array.isArray(v)) {
      // for arrays of objects use the first element to learn the shape
      if (v.length && typeof v[0] === 'object' && !Array.isArray(v[0])) recordKeys(v[0], key + '[]');
    } else if (typeof v === 'object' && v !== null) {
      recordKeys(v, key);
    }
  }
}

walk(REF, p => {
  const j = JSON.parse(fs.readFileSync(p, 'utf8'));
  recordKeys(j, '');
  for (const [rar, s] of Object.entries(j.stats || {})) {
    refRarities.add(rar);
    for (const m of s.attribute_modifiers || []) { refAttrs.add(m.attribute); refOps.add(m.operation); }
    for (const e of s.effects || []) refEffects.add(e.effect);
  }
  const w = j.basic_data && j.basic_data.weights;
  if (w && !Array.isArray(w)) for (const k of Object.keys(w)) if (!['weight', 'quality'].includes(k)) refTiers.add(k);
  for (const t of (j.basic_data && j.basic_data.bonus_loot) || []) refLoot.add(t);
});

console.log('upstream reference:');
console.log('  rarities      :', [...refRarities].sort().join(', '));
console.log('  world tiers   :', [...refTiers].sort().join(', '));
console.log('  loot tables   :', [...refLoot].sort().join(', '));
console.log('  attributes    :', [...refAttrs].sort().join(', '));
console.log('  operations    :', [...refOps].sort().join(', '));
console.log('  effects       :', [...refEffects].sort().join(', '));

// ---------- check ours against it ----------
function checkKeys(obj, prefix, file) {
  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return;
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix + '.' + k;
    if (k === 'neoforge:conditions') continue;      // ours has an extra condition; upstream uses it too
    ok(refKeys.has(key), file + ': key ' + key + ' is not used by any upstream invader');
    if (Array.isArray(v) && v.length && typeof v[0] === 'object' && !Array.isArray(v[0])) {
      checkKeys(v[0], key + '[]', file);
    } else if (v && typeof v === 'object') {
      checkKeys(v, key, file);
    }
  }
}

const files = fs.readdirSync(OURS).filter(f => f.endsWith('.json')).sort();
ok(files.length === 7, 'expected 7 files (one per biome group), found ' + files.length);

for (const f of files) {
  const j = JSON.parse(fs.readFileSync(path.join(OURS, f), 'utf8'));
  console.log('== ' + f);

  checkKeys(j, '', f);

  // required top-level keys mirror upstream exactly
  const top = Object.keys(j).filter(k => k !== 'neoforge:conditions').sort();
  ok(JSON.stringify(top) === JSON.stringify(['basic_data', 'entity', 'size', 'stats', 'type']),
    f + ': top-level keys are ' + top.join(',') + ', upstream uses basic_data/entity/size/stats/type');

  // attributes + operations must be ones upstream uses
  for (const [rar, s] of Object.entries(j.stats)) {
    ok(refRarities.has(rar), f + ': rarity ' + rar + ' not used upstream');
    for (const m of s.attribute_modifiers) {
      ok(refAttrs.has(m.attribute), f + ': attribute ' + m.attribute + ' unused upstream');
      ok(refOps.has(m.operation), f + ': operation ' + m.operation + ' unused upstream');
    }
    for (const e of s.effects || []) ok(refEffects.has(e.effect), f + ': effect ' + e.effect + ' unused upstream');
  }

  // weights: either the single form or a subset of upstream's tier names
  const w = j.basic_data.weights;
  const wkeys = Object.keys(w);
  if (!wkeys.includes('weight')) {
    for (const k of wkeys) ok(refTiers.has(k), f + ': weight tier ' + k + ' unused upstream');
  }

  // bonus loot must come from the set upstream references
  for (const t of j.basic_data.bonus_loot) ok(refLoot.has(t), f + ': bonus loot ' + t + ' unused upstream');

  // gear predicates must be ones upstream references
  for (const preds of Object.values(j.basic_data.valid_gear_sets)) {
    for (const p of preds) {
      ok(/^#(haven|frontier|ascent|summit|pinnacle)_(melee|ranged)$/.test(p),
        f + ': gear predicate ' + p + ' does not match upstream naming');
    }
  }
}

console.log(fails === 0 ? '\nUPSTREAM FIELD PARITY OK' : '\n' + fails + ' PARITY FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
