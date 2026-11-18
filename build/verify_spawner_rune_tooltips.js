// Statically verifies the spawner-rune tooltip feature.
//
// The feature claims four things that can each break silently, so each is checked against real jars rather than
// against the source text alone:
//
//   1. the API it calls still exists in Apothic Spawners (recipe type, SpawnerModifier getters, StatModifier record
//      accessors, SpawnerStat#formatValue) - a rename there compiles fine here and fails at runtime
//   2. the data the tooltip prints is really in the recipes: every shipped rune has a modifier recipe, and the
//      upgrade runes carry the five stat changes the feature is meant to surface
//   3. optional-mod isolation: every Apothic Spawners reference is confined to the guarded class, and the guarded
//      class is only touched after the mod-list check
//   4. hot-reload wiring: the memo is invalidated from a recipe event, not cached forever
//
// Run from the project root:  node build/verify_spawner_rune_tooltips.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { ROOT, rel, apothJar, javap, optionalEnv } = require('./lib_paths');

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const SRC = rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion');
const read = (p) => fs.readFileSync(p, 'utf8');

/**
 * Removes comments so a check cannot be fooled by prose.
 *
 * Both checks below ask "does this code reference X", and a javadoc paragraph that names a class in order to explain
 * why it is *not* used must not count as a reference.
 */
function stripComments(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/[^\n]*/g, '');
}

const tooltipsRaw = read(path.join(SRC, 'spawnerrune', 'SpawnerRuneTooltips.java'));
const tooltips = stripComments(tooltipsRaw);
const gateRaw = read(path.join(SRC, 'spawnerrune', 'SpawnerRuneIntegration.java'));
const gate = stripComments(gateRaw);
const clientRaw = read(path.join(SRC, 'ClientIntegration.java'));
const client = stripComments(clientRaw);
const main = stripComments(read(path.join(SRC, 'ApotheosisExpansion.java')));
const cfg = read(path.join(SRC, 'Config.java'));

// ---------------------------------------------------------------- 0. locate Apothic Spawners
console.log('0) Apothic Spawners jar');
let AS_JAR = optionalEnv('APOTHIC_SPAWNERS_JAR');
if (!AS_JAR) {
  const libs = rel('libs');
  if (fs.existsSync(libs)) {
    const hit = fs.readdirSync(libs).find(f => /^ApothicSpawners-.*\.jar$/i.test(f));
    if (hit) AS_JAR = path.join(libs, hit);
  }
}
ok(!!AS_JAR, 'Apothic Spawners jar found (set APOTHIC_SPAWNERS_JAR to override)');
if (!AS_JAR) {
  console.log('\nCannot continue without the Apothic Spawners jar.');
  process.exit(1);
}

const JAVA = javap();
const listing = execFileSync('unzip', ['-l', AS_JAR], { encoding: 'utf8' });
const runJavap = (...args) => execFileSync(JAVA, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

// ---------------------------------------------------------------- 1. API surface the feature calls
console.log('\n1) the Apothic Spawners API the tooltip calls still exists');
{
  // ASObjects.SPAWNER_MODIFIER is the RecipeType the scan iterates.
  const asObjects = runJavap('-p', '-classpath', AS_JAR, 'dev.shadowsoffire.apothic_spawners.ASObjects');
  ok(/SPAWNER_MODIFIER\s*;\s*$/m.test(asObjects) || asObjects.includes('SPAWNER_MODIFIER'),
    'ASObjects.SPAWNER_MODIFIER exists (the recipe type being scanned)');

  // SpawnerModifier: main-hand ingredient + the stat list.
  const modifier = runJavap('-p', '-classpath', AS_JAR, 'dev.shadowsoffire.apothic_spawners.modifiers.SpawnerModifier');
  ok(/public net\.minecraft\.world\.item\.crafting\.Ingredient getMainhandInput\(\)/.test(modifier),
    'SpawnerModifier#getMainhandInput() returns Ingredient');
  ok(/public net\.minecraft\.world\.item\.crafting\.Ingredient getOffhandInput\(\)/.test(modifier),
    'SpawnerModifier#getOffhandInput() returns Ingredient');
  ok(/public java\.util\.List<.*StatModifier.*> getStatModifiers\(\)/.test(modifier),
    'SpawnerModifier#getStatModifiers() returns the stat list');

  // StatModifier is a record: the tooltip reads stat/value/min/max/mode and calls getFormattedValue.
  const stat = runJavap('-p', '-classpath', AS_JAR, 'dev.shadowsoffire.apothic_spawners.modifiers.StatModifier');
  for (const accessor of ['stat()', 'value()', 'min()', 'max()', 'mode()']) {
    ok(new RegExp('public .* ' + accessor.replace('()', '\\(\\)')).test(stat),
      'StatModifier#' + accessor + ' is a record accessor');
  }
  ok(stat.includes('getFormattedValue()'), 'StatModifier#getFormattedValue() exists (used for the value text)');
  ok(stat.includes('Optional'), 'StatModifier exposes Optional bounds, which the clamp line reads');

  // Mode is an enum with SET and ADD - the branch in describe() keys off both names.
  const mode = runJavap('-p', '-classpath', AS_JAR, 'dev.shadowsoffire.apothic_spawners.modifiers.StatModifier$Mode');
  ok(/public static final .*Mode SET;/.test(mode), 'StatModifier.Mode.SET exists');
  ok(/public static final .*Mode ADD;/.test(mode), 'StatModifier.Mode.ADD exists');

  // SpawnerStat: name/formatValue are what produce the translated stat name and its unit.
  const spawnerStat = runJavap('-p', '-classpath', AS_JAR, 'dev.shadowsoffire.apothic_spawners.stats.SpawnerStat');
  ok(spawnerStat.includes('name()'), 'SpawnerStat#name() exists');
  ok(spawnerStat.includes('formatValue('), 'SpawnerStat#formatValue(T) exists');
}

// ---------------------------------------------------------------- 2. the shipped recipes
console.log('\n2) every shipped rune really has a modifier recipe');
// Recipe data lives in the Apotheosis jar, not in Apothic Spawners: that mod contributes the recipe *type* and the
// stat registry, while the rune definitions are shipped by Apotheosis itself under data/apotheosis/.
const APOTH = apothJar();
const apothListing = execFileSync('unzip', ['-l', APOTH], { encoding: 'utf8' });
const recipes = apothListing
  .split('\n')
  .map(line => line.trim().split(/\s+/).pop())
  .filter(name => name && name.includes('spawner_modifiers/') && name.endsWith('.json'));

/** Reads one json entry out of a jar. */
const readJson = (jar, entry) => JSON.parse(execFileSync('unzip', ['-p', jar, entry], { encoding: 'utf8' }));

const modifiers = recipes
  .map(entry => ({ entry, json: readJson(APOTH, entry) }))
  .filter(r => r.json.type === 'apothic_spawners:spawner_modifier');

ok(modifiers.length > 0, 'found ' + modifiers.length + ' spawner_modifier recipes');

// Only main-hand-only recipes describe a rune the way the tooltip shows it; the _inverse ones are off-hand variants.
const forward = modifiers.filter(r => !r.json.offhand);
const inverse = modifiers.filter(r => r.json.offhand);
ok(inverse.length > 0, 'the pack ships ' + inverse.length + ' off-hand (inverse) variants, which the tooltip must skip');
ok(forward.every(r => r.json.mainhand && r.json.stat_changes && r.json.stat_changes.length > 0),
  'every forward recipe has a mainhand ingredient and at least one stat change');

// The four upgrade runes are what the user reported as unreadable: assert their full five-stat profile is present,
// because a truncated recipe would make the new tooltip quietly wrong rather than empty.
const upgrades = ['frontier', 'ascent', 'summit', 'pinnacle'];
for (const tier of upgrades) {
  const entry = 'data/apotheosis/recipe/spawner_modifiers/tier/' + tier + '.json';
  const found = modifiers.find(r => r.entry === entry);
  ok(!!found, tier + ' upgrade rune has a modifier recipe');
  if (!found) continue;
  ok(found.json.stat_changes.length === 5, entry + ' lists all 5 stats (got ' + found.json.stat_changes.length + ')');
  ok(found.json.stat_changes.every(s => s.mode === 'set'),
    entry + ' is fully "set" mode, so the tooltip prints plain values');
  ok(found.json.stat_changes.every(s => typeof s.value === 'number'),
    entry + ' values are numeric (no boolean branch is needed for it)');
}

// Boolean runes exercise the "Enables X" branch; level/percentage runes exercise the additive branch.
const booleanRunes = forward.filter(r => r.json.stat_changes.every(s => typeof s.value === 'boolean'));
const additiveRunes = forward.filter(r => r.json.stat_changes.some(s => s.mode !== 'set'));
ok(booleanRunes.length > 0, 'at least one rune uses the boolean ("Enables X") branch: ' +
  booleanRunes.map(r => path.basename(r.entry)).join(', '));
ok(additiveRunes.length > 0, 'at least one rune uses the additive branch: ' +
  additiveRunes.map(r => path.basename(r.entry)).join(', '));
ok(additiveRunes.some(r => (r.json.stat_changes || []).some(s => s.min !== undefined || s.max !== undefined)),
  'at least one additive rune is clamped, which the tooltip appends as a bound');

// ---------------------------------------------------------------- 3. optional-mod isolation
console.log('\n3) Apothic Spawners is isolated behind the mod-list gate');
{
  // Rule: no file outside spawnerrune/ may mention the Apothic Spawners package.
  const offenders = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name === 'spawnerrune') continue;
        walk(full);
      }
      else if (entry.name.endsWith('.java')) {
        const text = fs.readFileSync(full, 'utf8');
        if (text.includes('dev.shadowsoffire.apothic_spawners')) offenders.push(path.relative(ROOT, full));
      }
    }
  };
  walk(SRC);
  ok(offenders.length === 0,
    'no Apothic Spawners reference outside spawnerrune/' + (offenders.length ? ': ' + offenders.join(', ') : ''));

  // The gate class itself must not reference the package as a *type*, or loading it would throw on a pack without the
  // mod. A string literal naming a class for a resource probe is fine - that is the whole point of PROBE_CLASS - so
  // strings are stripped before the check.
  const gateNoStrings = gate.replace(/"(?:[^"\\]|\\.)*"/g, '""');
  ok(!gateNoStrings.includes('dev.shadowsoffire.apothic_spawners'),
    'SpawnerRuneIntegration (the gate) holds no Apothic Spawners type (a probe string is allowed)');

  // The gate must NOT depend on ModList alone. In a real client session ModList.isLoaded returned false for
  // apothic_spawners, minihud and carpet even though all three were installed (see the CompatMixinPlugin log lines),
  // which silently disabled the whole feature. The class-resource probe is the reliable signal.
  ok(/getResource\(/.test(gate),
    'the gate probes for the ASObjects class resource instead of trusting ModList');
  ok(/load|resource/.test(gate) && /\.class/.test(gate),
    'the probe asks for the .class resource (without defining the class)');
  ok(/byClass\s*\|\|\s*byModList/.test(gate),
    'the two signals are OR-ed: ModList may only turn the feature on, never off');
  ok(/TARGET_MODID\s*=\s*"apothic_spawners"/.test(gate), 'TARGET_MODID is apothic_spawners');
  ok(/PROBE_CLASS\s*=\s*"dev\.shadowsoffire\.apothic_spawners/.test(gate),
    'PROBE_CLASS names an Apothic Spawners class as a string (so the gate itself stays loadable)');
  ok(/catch \(RuntimeException \| LinkageError/.test(gate),
    'a probe failure degrades to a logged answer instead of crashing');

  // A cached "false" is what made this bug so hard to see: the gate runs once at construction and never re-checks.
  ok(/available\s*=\s*byClass\s*\|\|\s*byModList/.test(gate),
    'the combined answer is what gets cached');
  ok(/LOGGER\.info/.test(gate),
    'the gate logs its verdict, so a disabled feature is visible in the log');

  // Main class: the tooltip registration sits inside the client-dist branch AND behind the availability check.
  const clientBlock = main.slice(main.indexOf('FMLEnvironment.dist.isClient()'));
  ok(clientBlock.includes('registerSpawnerRuneTooltips()'),
    'the mod constructor registers the listeners');
  ok(/SpawnerRuneIntegration\.isAvailable\(\)[\s\S]{0,200}registerSpawnerRuneTooltips\(\)/.test(clientBlock),
    'registration is guarded by isAvailable(), so a pack without the mod never links the tooltip class');

  // Client-only events must not be registered from shared code.
  ok(client.includes('RecipesUpdatedEvent'), 'client wiring listens for RecipesUpdatedEvent (a client-only class)');
  ok(!gate.includes('RecipesUpdatedEvent'), 'the shared gate does not mention the client-only event');
  ok(client.includes('ItemTooltipEvent'), 'client wiring listens for ItemTooltipEvent');

  // The tooltip context is NOT always a level-carrying one: EMI (and the startup search tree) build tooltips through
  // Item.TooltipContext.of(HolderLookup.Provider), whose level() returns null. Passing context.level() straight through
  // made the stats vanish exactly in the recipe-viewer panel players hover, which is what happened in testing.
  ok(/levelFor\(/.test(client), 'the client wiring resolves the level through a helper');
  ok(/getContext\(\)\.level\(\)/.test(client), 'the helper still asks the tooltip context first');
  ok(/Minecraft\.getInstance\(\)\.level/.test(client),
    'it falls back to the client level when the context has none (EMI / search-tree tooltips)');
  ok(!/append\([^)]*getContext\(\)\.level\(\)/.test(client),
    'append() is never called with a possibly-null context level directly');
}

// ---------------------------------------------------------------- 4. the data actually drives it
console.log('\n4) values come from the recipe, not from a hard-coded table');
{
  // A hard-coded copy would show up as stat ids, numbers or item ids in the tooltip source.
  const hardCodedNumbers = [...tooltips.matchAll(/\b(?:150|600|32|24|48|16|13|10|7)\b/g)].map(m => m[0]);
  ok(hardCodedNumbers.length === 0,
    'no recipe values are duplicated in the code' + (hardCodedNumbers.length ? ': ' + hardCodedNumbers.join(', ') : ''));
  ok(!/frontier|ascent|summit|pinnacle/.test(tooltips),
    'no rune item name is hard-coded (a new rune needs no code change)');

  // ...and it must read the recipes at runtime.
  ok(tooltips.includes('getAllRecipesFor('), 'the scan asks the recipe manager for all recipes');
  ok(tooltips.includes('SPAWNER_MODIFIER'), 'it scans the spawner_modifier recipe type');
  ok(tooltips.includes('getStatModifiers()'), 'it prints the decoded stat changes');
  ok(tooltips.includes('getFormattedValue()'), 'values go through the stat formatter');
  ok(tooltips.includes('formatValue('), 'the clamp bounds go through the stat formatter too');

  // Data packs reload: the memo has to be droppable.
  ok(/CACHE\.clear\(\)/.test(tooltips), 'the memo is cleared on invalidate()');
  ok(/RecipesUpdatedEvent[\s\S]{0,160}invalidate\(\)/.test(client),
    'a new recipe set invalidates the memo, so /reload is reflected');

  // No logging on the per-item path. A recipe viewer indexes the entire item registry on its baking thread, so a log
  // line inside the scan produced ~3800 lines per launch for a mod that only wants to describe ~17 items.
  const scanBody = tooltips.slice(tooltips.indexOf('private static List<StatModifier<?>> scan('));
  const scanFn = scanBody.slice(0, scanBody.indexOf('\n    private static boolean matches'));
  ok(!/LOGGER\./.test(scanFn),
    'the per-item scan path contains no logging (it runs once per indexed item)');
  // The candidate list must be memoised per recipe table, not copied on every item.
  ok(/cachedRecipeManager/.test(tooltips),
    'the spawner modifier recipe list is memoised per RecipeManager instance');
  ok(/cachedFor == recipes/.test(tooltips),
    'the memo is keyed on the manager identity, so a reload re-reads it');
}

// ---------------------------------------------------------------- 5. the config switch
console.log('\n5) the feature is switchable');
{
  ok(cfg.includes('"enable_spawner_rune_tooltips"'), 'the option exists');

  // The define() result MUST be assigned to the field. A bare b.comment(...).define(...) still registers the entry -
  // the TOML gets written, the config screen shows a working toggle - but the field stays null, so the accessor
  // returns false forever and the feature silently never runs. That exact mistake shipped once.
  ok(/ENABLE_SPAWNER_RUNE_TOOLTIPS\s*=\s*b\s*\n?\s*\.comment\(/.test(cfg),
    'the define() result is assigned to ENABLE_SPAWNER_RUNE_TOOLTIPS');
  const defineBlock = cfg.slice(cfg.indexOf('if (isModLoaded("apothic_spawners"))'));
  ok(/ENABLE_SPAWNER_RUNE_TOOLTIPS\s*=\s*b/.test(defineBlock.slice(0, 400)),
    'the assignment sits inside the mod-gated block, right at its start');
  // Guard against the field being written but the accessor reading a different one.
  ok(/return ENABLE_SPAWNER_RUNE_TOOLTIPS != null && ENABLE_SPAWNER_RUNE_TOOLTIPS\.get\(\)/.test(cfg),
    'the accessor reads the same field that define() populates');

  ok(/isModLoaded\("apothic_spawners"\)/.test(cfg),
    'the sub-section is only registered when the mod is present');
  ok(/ENABLE_SPAWNER_RUNE_TOOLTIPS\s*(!=|==)\s*null/.test(cfg),
    'the accessor tolerates the option being absent (mod not installed)');
  ok(tooltips.includes('Config.enableSpawnerRuneTooltips()'),
    'the tooltip reads the switch per call, so it is hot-reloadable');
  ok(!/static final boolean.*enableSpawner/.test(tooltips),
    'the switch is not copied into a static field');

  for (const lang of ['en_us', 'zh_cn']) {
    const json = JSON.parse(fs.readFileSync(rel('src', 'main', 'resources', 'assets',
      'apotheosis_expansion', 'lang', lang + '.json'), 'utf8'));
    const label = 'apotheosis_expansion.configuration.spawner.enable_spawner_rune_tooltips';
    ok(label in json, lang + ' has the config label');
    ok(label + '.tooltip' in json, lang + ' has the config tooltip');
    for (const key of ['enables', 'disables', 'set', 'max', 'min']) {
      ok('apotheosis_expansion.spawner_rune.' + key in json, lang + ' has spawner_rune.' + key);
    }
  }
}

// ---------------------------------------------------------------- 6. every format key used exists
console.log('\n6) every translation key the code uses is defined');
{
  const used = [...tooltips.matchAll(/"(apotheosis_expansion\.spawner_rune\.[a-z_]+)"/g)].map(m => m[1]);
  const json = JSON.parse(fs.readFileSync(rel('src', 'main', 'resources', 'assets',
    'apotheosis_expansion', 'lang', 'en_us.json'), 'utf8'));
  for (const key of new Set(used)) {
    ok(key in json, 'en_us defines ' + key);
  }
  ok(used.length > 0, 'the tooltip source references ' + new Set(used).size + ' of its own keys');
}

console.log(fails === 0 ? '\nSPAWNER RUNE TOOLTIPS OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
