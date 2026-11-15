// Cross-checks the three places that must agree about per-entity invader switches:
//   1. EntityInvaderEnabled.SUPPORTED  (Java: entity id -> config key suffix)
//   2. Config.java                     (Java: the BooleanValue declarations + entitySwitch calls)
//   3. the data files                  (JSON: which entity each entry gates on)
//
// A mismatch in any direction is a real bug:
//   * a data file naming an unknown entity   -> permanently enabled, switch silently does nothing
//   * SUPPORTED naming an entity with no file -> a dead switch in the GUI
//   * a config key with no lang entry         -> raw key shown in the GUI
const fs = require('fs');
const path = require('path');

const JAVA = 'src/main/java/com/tamamo/apotheosisexpansion/compat/EntityInvaderEnabled.java';
const CFG = 'src/main/java/com/tamamo/apotheosisexpansion/Config.java';
const ROOT = 'src/main/resources/data/apotheosis/apothic_invaders';
const DIRS = ['thermal', 'mekanism', 'ars_nouveau', 'allthemodium', 'vanilla_bosses'];

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

// ---- 1. SUPPORTED from the Java source
const java = fs.readFileSync(JAVA, 'utf8');
const supported = new Map();
for (const m of java.matchAll(/m\.put\("([^"]+)",\s*"([^"]+)"\);/g)) {
  supported.set(m[1], m[2]);
}
console.log('SUPPORTED entries: ' + supported.size);
ok(supported.size === 15, 'expected 15 supported entities (12 compat + 3 vanilla bosses), found ' + supported.size);

// ---- 2. Config.java
const cfg = fs.readFileSync(CFG, 'utf8');

// every SUPPORTED entity must have an entitySwitch(...) call
// PLAN3: verify each entity's switch is registered inside the right gate
const GATES = {
  "thermal:": "thermal",
  "mekanismadditions:": "mekanismadditions",
  "ars_nouveau:": "ars_nouveau",
  "allthemodium:": "allthemodium",
  "minecraft:": null, // vanilla bosses: always registered
};
for (const [entity, key] of supported) {
  ok(cfg.includes('entitySwitch(b, "' + entity + '"'),
    'Config has an entitySwitch call for ' + entity);
  ok(cfg.includes('.define(key, def)') || cfg.includes('define(key'),
    'entitySwitch defines the value via the shared key variable');
  // the declaration field must exist and be assigned from entitySwitch
  ok(new RegExp('ENABLE_[A-Z0-9_]+ = entitySwitch\\(b, "' + entity + '"').test(cfg),
    'Config assigns a BooleanValue for ' + entity);
}

// every declared BooleanValue must be assigned exactly once
const declared = [...cfg.matchAll(/public static final BooleanValue (ENABLE_[A-Z0-9_]+);/g)].map(m => m[1]);
for (const name of declared) {
  const assignments = (cfg.match(new RegExp('^\\s*' + name + ' = ', 'gm')) || []).length;
  ok(assignments === 1, name + ' assigned exactly once (found ' + assignments + ')');
}

// ---- 3. data files
const entityToFiles = new Map();
for (const dir of DIRS) {
  const full = path.join(ROOT, dir);
  for (const f of fs.readdirSync(full).filter(x => x.endsWith('.json'))) {
    const j = JSON.parse(fs.readFileSync(path.join(full, f), 'utf8'));
    if (!entityToFiles.has(j.entity)) entityToFiles.set(j.entity, []);
    entityToFiles.get(j.entity).push(f);

    const conds = j['neoforge:conditions'] || [];
    const entConds = conds.filter(c => c.type === 'apotheosis_expansion:entity_invader_enabled');
    ok(entConds.length === 1, dir + '/' + f + ': exactly one entity condition');
    if (entConds.length === 1) {
      ok(entConds[0].entity === j.entity,
        dir + '/' + f + ': condition entity matches the file entity (' + j.entity + ')');
    }
    // vanilla bosses: group + entity (+ mod_loaded for the ancient dragon variant); compat: mod_loaded + group + entity
    // Identify by folder name after the rename. The vanilla boss folder is the only one whose
    // entries are consumed by event hooks rather than by data-driven spawn selection, which is why
    // its condition list omits the per-file mod_loaded gate for the non-ancient entries.
    const isBossDir = dir === 'vanilla_bosses';
    const hasModLoaded = conds.some(c => c.type === 'neoforge:mod_loaded');
    const expected = isBossDir ? (hasModLoaded ? 3 : 2) : 3;
    ok(conds.length === expected, dir + '/' + f + ': ' + expected + ' conditions (got ' + conds.length + ')');
    ok(conds[conds.length - 1].type === 'apotheosis_expansion:entity_invader_enabled',
      dir + '/' + f + ': entity condition is last');
  }
}

console.log('\ndata entities: ' + entityToFiles.size);
for (const entity of entityToFiles.keys()) {
  ok(supported.has(entity), 'data entity ' + entity + ' has a switch in SUPPORTED');
}
for (const entity of supported.keys()) {
  ok(entityToFiles.has(entity), 'SUPPORTED entity ' + entity + ' has at least one data file');
}

// ---- 4. lang coverage for every generated config key
for (const lang of ['en_us', 'zh_cn']) {
  const p = 'src/main/resources/assets/apotheosis_expansion/lang/' + lang + '.json';
  const json = JSON.parse(fs.readFileSync(p, 'utf8'));
  for (const [, key] of supported) {
    const full = 'apotheosis_expansion.configuration.compat.' + key;
    ok(full in json, lang + ' has ' + full);
    const tip = full + '.tooltip';
    ok(tip in json, lang + ' has ' + tip);
  }
}

// PLAN3: each mod group must be guarded by ModList.get().isLoaded(...)
console.log('\n== plan 3: mod-gated sub-sections');
for (const [prefix, gate] of Object.entries(GATES)) {
  const entities = [...supported.keys()].filter(e => e.startsWith(prefix));
  if (!entities.length) continue;
  if (gate === null) {
    ok(!cfg.includes("isModLoaded(\"minecraft\")"), prefix + " group must NOT be gated on minecraft");
    continue;
  }
  ok(cfg.includes("isModLoaded(\"" + gate + "\")"), prefix + " group is gated on isModLoaded(" + gate + ")");
}

// group toggles must be null-safe now that they may never be assigned
for (const acc of ['enableThermalInvaders', 'enableMekanismInvaders', 'enableArsNouveauInvaders', 'enableVanillaBosses']) {
  ok(new RegExp(acc + '\\(\\) \\{\\s*[^}]*!= null').test(cfg.replace(/\s+/g, ' ')),
    acc + '() is null-safe (field may be unassigned when the mod is absent)');
}

console.log(fails === 0 ? '\nPER-ENTITY SWITCH CONSISTENCY OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
