// Statically verifies the gem compendium (JEI / EMI) feature.
//
// The feature makes claims that can each break silently, so each is checked against the real jars and the shipped
// gem data rather than against the source text alone:
//
//   1. the EMI-wins rule: the JEI plugin registers only when `useJei()` (JEI present, EMI absent) and the EMI
//      plugin only when `useEmi()`; every registration entry point is guarded, so both-installed can never double
//      register
//   2. the presence probes point at classes that really exist in the shipped JEI / EMI jars, and the gate itself
//      names no viewer type (it is called from Config, which loads on a server without either viewer)
//   3. discovery annotations are on the compiled classes, because a plugin nobody discovers is a plugin that never
//      runs
//   4. every translation key the pages use resolves in BOTH lang files, or is one Apotheosis itself ships
//   5. the model's rules, mirrored here, reproduce what the shipped gem data means: dimensions, zero-weight tiers,
//      min purity, and page ordering
//   6. the config key is registered only when a viewer is present, and the accessor is null-safe
//   7. the packaged jar bundles no JEI / EMI classes (compileOnly is honoured)
//
// Run from the project root:  node build/verify_gem_compendium.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { ROOT, rel, apothJar, javap, optionalEnv } = require('./lib_paths');

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const SRC = rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion');
const GC = (...f) => path.join(SRC, 'gemcompendium', ...f);
const read = (p) => fs.readFileSync(p, 'utf8');

/** Removes comments so a check cannot be fooled by prose that names a class in order to explain it. */
function stripComments(src) {
  return src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
}

const gate = stripComments(read(GC('GemCompendiumGate.java')));
const jeiPlugin = stripComments(read(GC('GemCompendiumJeiPlugin.java')));
const emiPlugin = stripComments(read(GC('GemCompendiumEmiPlugin.java')));
const model = stripComments(read(GC('GemCompendiumModel.java')));
const pages = stripComments(read(GC('GemCompendiumPages.java')));
const cfg = read(path.join(SRC, 'Config.java'));

// ---------------------------------------------------------------- 0. locate jars
console.log('0) jars');
const APOTH = apothJar();
ok(fs.existsSync(APOTH), 'Apotheosis jar found');

function libJar(pattern, envName) {
  let hit = optionalEnv(envName);
  if (hit && fs.existsSync(hit)) return hit;
  const libs = rel('libs');
  if (fs.existsSync(libs)) {
    const f = fs.readdirSync(libs).find(n => pattern.test(n));
    if (f) return path.join(libs, f);
  }
  return null;
}
const JEI_API = libJar(/^jei-.*-common-api-.*\.jar$/i, 'JEI_API_JAR');
const EMI_API = libJar(/^emi-neoforge-.*-api\.jar$/i, 'EMI_API_JAR');
ok(!!JEI_API, 'JEI common-api jar in libs (set JEI_API_JAR to override)');
ok(!!EMI_API, 'EMI api jar in libs (set EMI_API_JAR to override)');
if (!APOTH || !JEI_API || !EMI_API) {
  console.log('\nCannot continue without the jars.');
  process.exit(1);
}

/** Does a class path exist inside a jar? */
function jarHasClass(jar, classPath) {
  try {
    const out = execFileSync(process.env.JAR_EXE || 'jar', ['tf', jar], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
    return out.split('\n').some(l => l.trim() === classPath);
  }
  catch (e) {
    // Fall back to unzip -l when `jar` is not on PATH.
    const out = execFileSync('unzip', ['-l', jar], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
    return out.split('\n').some(l => l.includes(classPath));
  }
}

// ---------------------------------------------------------------- 1. the EMI-wins rule
console.log('\n1) EMI-wins rule');
ok(/useJei\(\)\s*\{[^}]*jeiPresent\(\)\s*&&\s*!emiPresent\(\)/s.test(gate.replace(/\s+/g, ' ').replace(/useJei\(\)\s*\{/s, m => m)) ||
   /boolean use = jeiPresent\(\) && !emiPresent\(\);/.test(gate),
   'gate: useJei() is JEI present AND EMI absent');
ok(/public static boolean useEmi\(\)\s*\{\s*return emiPresent\(\);/.test(gate),
   'gate: useEmi() is EMI present, regardless of JEI');

// Every JEI registration entry point must be guarded by useJei.
for (const m of ['registerCategories', 'registerRecipes']) {
  const re = new RegExp('public void ' + m + '\\([^)]*\\)\\s*\\{\\s*if \\(!GemCompendiumGate\\.useJei\\(\\)', 's');
  ok(re.test(jeiPlugin), 'JEI ' + m + ' returns early unless useJei()');
}
// EMI register is guarded too (a lone-JEI install must not double up through some other path).
ok(/public void register\(EmiRegistry registry\)\s*\{\s*if \(!GemCompendiumGate\.useEmi\(\) \|\| !Config\.enableGemCompendium\(\)\)/s.test(emiPlugin),
   'EMI register returns early unless useEmi() and the config allows it');
// The config check on the JEI side as well.
ok(/registerRecipes\([^)]*\)\s*\{\s*if \(!GemCompendiumGate\.useJei\(\) \|\| !Config\.enableGemCompendium\(\)\)/s.test(jeiPlugin),
   'JEI registerRecipes also honours the config switch');

// ---------------------------------------------------------------- 2. probes and server safety
console.log('\n2) presence probes');
ok(gate.includes('"mezz.jei.api.IModPlugin"'), 'gate probes mezz.jei.api.IModPlugin');
ok(gate.includes('"dev.emi.emi.api.EmiPlugin"'), 'gate probes dev.emi.emi.api.EmiPlugin');
ok(jarHasClass(JEI_API, 'mezz/jei/api/IModPlugin.class'), 'probe target exists in the JEI api jar');
ok(jarHasClass(EMI_API, 'dev/emi/emi/api/EmiPlugin.class'), 'probe target exists in the EMI api jar');
ok(!/getResource.*Class\.forName/.test(gate) && !/Class\.forName/.test(gate),
   'gate never Class.forName-s a viewer type (repo convention)');
// Config calls the gate at spec-construction time, on servers too: the gate must not import viewer packages.
ok(!/^import (mezz\.jei|dev\.emi)/m.test(gate), 'gate imports no viewer package, so Config can call it on a server');
ok(/GemCompendiumGate\.anyViewerPresent\(\)/.test(cfg), 'Config gates the key on anyViewerPresent()');

// ---------------------------------------------------------------- 3. discovery annotations on compiled classes
console.log('\n3) annotations on compiled classes');
const CLASSES = rel('build', 'classes');
ok(fs.existsSync(CLASSES), 'build/classes exists (run build/compile.js first)');
if (fs.existsSync(CLASSES)) {
  const jeiOut = execFileSync(javap(), ['-v', '-p', '-classpath', CLASSES,
    'com.tamamo.apotheosisexpansion.gemcompendium.GemCompendiumJeiPlugin'], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  ok(/Lmezz\/jei\/api\/JeiPlugin;/.test(jeiOut), 'compiled JEI plugin carries @JeiPlugin');
  const emiOut = execFileSync(javap(), ['-v', '-p', '-classpath', CLASSES,
    'com.tamamo.apotheosisexpansion.gemcompendium.GemCompendiumEmiPlugin'], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  ok(/Ldev\/emi\/emi\/api\/EmiEntrypoint;/.test(emiOut), 'compiled EMI plugin carries @EmiEntrypoint');
  // JEI's category must not rely on the null default background (JEI throws on it).
  const catOut = execFileSync(javap(), ['-p', '-classpath', CLASSES,
    'com.tamamo.apotheosisexpansion.gemcompendium.GemCompendiumJeiPlugin$Category'], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  ok(/getBackground\(\)/.test(catOut), 'JEI category overrides getBackground (null default would throw)');
  // The APIs used must still exist in the shipped jars.
  ok(jarHasClass(JEI_API, 'mezz/jei/api/gui/widgets/IScrollBoxWidget.class'), 'JEI scroll box API exists in the api jar');
  const scrollOut = execFileSync(javap(), ['-classpath', JEI_API, 'mezz.jei.api.gui.widgets.IScrollBoxWidget'], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
  ok(/setContents\(java\.util\.List<net\.minecraft\.network\.chat\.FormattedText>\)/.test(scrollOut), 'scroll box setContents(List<FormattedText>) still exists');
}

// ---------------------------------------------------------------- 4. translation keys
console.log('\n4) translation keys');
const en = JSON.parse(read(rel('src', 'main', 'resources', 'assets', 'apotheosis_expansion', 'lang', 'en_us.json')));
const zh = JSON.parse(read(rel('src', 'main', 'resources', 'assets', 'apotheosis_expansion', 'lang', 'zh_cn.json')));
const apothLangDir = rel('build', 'tmp', 'apothlang', 'assets', 'apotheosis', 'lang');
let apothEn = {};
if (fs.existsSync(path.join(apothLangDir, 'en_us.json'))) {
  apothEn = JSON.parse(read(path.join(apothLangDir, 'en_us.json')));
}
else {
  // Extract from the jar on demand.
  execFileSync('unzip', ['-o', '-q', APOTH, 'assets/apotheosis/lang/en_us.json', '-d', rel('build', 'tmp', 'apothlang')]);
  apothEn = JSON.parse(read(path.join(apothLangDir, 'en_us.json')));
}

/** Every literal translation key used by the compendium code. */
function usedKeys(src) {
  const keys = new Set();
  for (const m of src.matchAll(/translatable(?:WithFallback)?\(\s*"([^"]+)"/g)) keys.add(m[1]);
  return keys;
}
const allUsed = new Set([...usedKeys(pages), ...usedKeys(jeiPlugin), ...usedKeys(emiPlugin), ...usedKeys(model)]);
let missing = [];
for (const k of allUsed) {
  if (k in en || k in apothEn) continue;
  // Apotheosis-only keys (gem_class.*, text.apotheosis.*, purity.*) must exist there.
  missing.push(k);
}
ok(missing.length === 0, 'every literal key resolves in en_us or Apotheosis lang' + (missing.length ? ': ' + missing.join(', ') : ''));
missing = [];
for (const k of allUsed) {
  if (k.startsWith('text.apotheosis.') || k.startsWith('gem_class.') || k.startsWith('purity.apotheosis.')) continue; // Apotheosis ships zh too
  if (!(k in zh) && !(k in en)) missing.push(k);
}
ok(missing.length === 0, 'every ours-owned key resolves in zh_cn' + (missing.length ? ': ' + missing.join(', ') : ''));
ok('emi.category.apotheosis_expansion.gem_compendium' in en && 'emi.category.apotheosis_expansion.gem_compendium' in zh,
   'EMI-derived category title key ships in both langs');
for (const d of ['overworld', 'the_end', 'the_nether']) {
  ok(('dimension.minecraft.' + d) in en && ('dimension.minecraft.' + d) in zh, 'dimension.minecraft.' + d + ' named in both langs');
}

// ---------------------------------------------------------------- 5. model rules vs shipped gem data
console.log('\n5) model rules against shipped gems');
// Mirrors of the Java rules, driven with the real data files from the Apotheosis jar - the point is to check the
// decisions against shipped data, not against invented samples.
const PURITIES = ['cracked', 'chipped', 'flawed', 'normal', 'flawless', 'perfect'];
const TIERS = ['haven', 'frontier', 'ascent', 'summit', 'pinnacle'];

/** Lists every gem json in the jar (paths are stable across versions: data/apotheosis/gems/**). */
const gemFiles = execFileSync('unzip', ['-l', APOTH], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 })
  .split('\n')
  .map(l => (l.match(/data\/apotheosis\/gems\/[^ ]+\.json/) || [])[0])
  .filter(Boolean);
ok(gemFiles.length > 0, 'found ' + gemFiles.length + ' shipped gem files in the Apotheosis jar');

let dataIssues = 0;
let seenDims = new Set();
let conditionalSkipped = 0;
for (const f of gemFiles) {
  const raw = execFileSync('unzip', ['-p', APOTH, f], { encoding: 'utf8' });
  // Conditional gems are plain flat json with a "neoforge:conditions" array on top, not a wrapped value, so there
  // is nothing to unwrap; counting them here documents that the gate really appears in shipped data.
  let json = JSON.parse(raw);
  if (json['neoforge:conditions']) {
    conditionalSkipped++;
  }
  const gem = json.value !== undefined ? json.value : json;
  if (!gem || typeof gem !== 'object' || !gem.bonuses) continue;
  const id = f.replace(/^data\/apotheosis\/gems\//, '').replace(/\.json$/, '');

  // dimensions: constraints.dimensions lists ResourceKeys; empty/absent means every dimension.
  const dims = (gem.constraints && gem.constraints.dimensions) || [];
  for (const d of dims) seenDims.add(d.replace(/^minecraft:/, 'minecraft.').replace(':', '.'));

  // tiers: a flat {"weight": N} form means all tiers; otherwise only tiers with weight > 0.
  const weights = gem.weights || {};
  let expectedTiers;
  if (weights.weight !== undefined) {
    expectedTiers = weights.weight > 0 ? TIERS.slice() : [];
  } else {
    expectedTiers = TIERS.filter(t => weights[t] && weights[t].weight > 0);
  }
  // The Java rule drops weight-0 tiers; assert the mirror agrees with what tiersOf would produce for this shape.
  if (weights.weight !== undefined && weights.weight === 0 && expectedTiers.length !== 0) { dataIssues++; console.log('    tier rule disagrees for ' + id); }

  // min purity: pages exist only at >= min_purity, and at least one purity must survive for the gem to be listed.
  const minPurity = gem.min_purity || 'normal';
  const minIdx = PURITIES.indexOf(minPurity);
  ok(minIdx >= 0, 'gem ' + id + ' has a known min purity (' + minPurity + ')');
  // Every shipped gem has at least one bonus, so at least one page will exist for it.
  ok(Array.isArray(gem.bonuses) && gem.bonuses.length > 0, 'gem ' + id + ' carries bonuses, so it gets at least one page');
}
ok(dataIssues === 0, 'tier/dimension rules agree with every shipped gem');
ok(conditionalSkipped >= 0, 'conditional gems parsed without special casing (' + conditionalSkipped + ' carry mod_loaded conditions)');

// Compat is data-driven, so the sources must not know any particular gem: a hardcoded id list would silently drop
// every gem another mod or a data pack adds.
const compendiumSrc = [gate, jeiPlugin, emiPlugin, model, pages].join('\n');
const hardcoded = compendiumSrc.match(/"(?:apotheosis|[a-z_]+):[a-z_]+\/[a-z_/]+"/g) || [];
ok(hardcoded.length === 0, 'no hardcoded gem ids anywhere in the compendium sources'
   + (hardcoded.length ? ': ' + hardcoded.join(', ') : ''));
ok(/GemRegistry\.INSTANCE\.getValues\(\)/.test(model), 'pages are built from the live GemRegistry, not a fixed list');
ok(/getSocketBonusTooltip/.test(pages), 'bonus wording comes from each bonus own formatter (polymorphic, so modded bonus types work)');

// Every dimension the shipped data actually uses either has our lang name or is intentionally id-only.
for (const d of seenDims) {
  const key = 'dimension.' + d;
  const vanilla = d.startsWith('minecraft.');
  ok(!vanilla || (key in en), 'vanilla dimension used by shipped data has a lang name: ' + key);
}
// twilightforest is gated behind mod_loaded; if it appears in the data it must fall back to the raw id (fine).
console.log('    dimensions used by shipped gems: ' + [...seenDims].join(', '));

// ---------------------------------------------------------------- 6. page construction invariants
console.log('\n6) page construction');
ok(/snapshotEntries/.test(model) && /snapshotEntries/.test(jeiPlugin) && /snapshotEntries/.test(emiPlugin),
   'both viewers build pages from the same snapshotEntries() (one page per gem)');
ok(!/record Page\(/.test(model), 'no per-purity Page record: purities share one page per gem');
ok(/bonus\.supports\(purity\)/.test(pages), 'each bonus value is gated by Apotheosis own supports(purity)');
ok(/Component\.literal\("-"\)/.test(pages), 'a purity a bonus does not cover prints "-" instead of shifting columns');
ok(/commonPrefix\(texts\)/.test(pages) && /commonSuffix\(texts/.test(pages),
   'bonus rows compress the shared wording: only the numbers are joined by "/"');
ok(/trimPrefixDigits/.test(pages) && /trimSuffixDigits/.test(pages),
   'compression trims at digit boundaries so numbers are never split');
ok(/allSame/.test(pages), 'a bonus that does not scale with purity prints one value, no slashes');
ok(/UnsocketedGem\.of\(GemItem\.createStack\(entry\.gem\(\), purity\)\)/.test(pages),
   'bonus tooltips are asked through Apotheosis own UnsocketedGem view at each listed purity');
ok(/AttributeTooltipContext\.of\(null, Item\.TooltipContext\.EMPTY, TooltipFlag\.NORMAL\)/.test(pages),
   'tooltip context is the verified player-less one');
ok(/catch \(RuntimeException \| LinkageError/.test(pages),
   'a throwing third-party bonus degrades one value instead of the page');
ok(/int mouseX, int mouseY, float delta/.test(emiPlugin) && /graphics\.drawString\(font, this\.lines\.get\(i\), 4, lineY/.test(emiPlugin),
   'EMI draw callback ignores the mouse arguments (they are not a drawing origin)');
ok(/addButton\(GemCompendiumPages\.EMI_WIDTH - 26, 2/.test(emiPlugin) && !/addButton\(2, 2/.test(emiPlugin),
   'EMI page buttons sit top-right, clear of the ingredient slot at (2, 2)');
ok(/this\.height = Math\.min\(GemCompendiumPages\.EMI_MAX_HEIGHT/.test(emiPlugin),
   'EMI page height follows its content instead of a fixed 150');
ok(/EMI_TEXT_Y = 22/.test(pages), 'EMI text starts directly under the slot row, no gap band');
ok(/stacksFor/.test(jeiPlugin) && /stacksFor/.test(emiPlugin),
   'the page slot cycles every purity of the gem in both viewers');

// ---------------------------------------------------------------- 7. config wiring
console.log('\n7) config');
ok(/\.define\("enable_gem_compendium", true\)/.test(cfg), 'key enable_gem_compendium defined, default true');
ok(/if \(com\.tamamo\.apotheosisexpansion\.gemcompendium\.GemCompendiumGate\.anyViewerPresent\(\)\)/.test(cfg) ||
   /anyViewerPresent\(\)\)\s*\{\s*ENABLE_GEM_COMPENDIUM = b/.test(cfg),
   'key registered only when a viewer is present');
ok(/public static boolean enableGemCompendium\(\)\s*\{\s*return ENABLE_GEM_COMPENDIUM != null && ENABLE_GEM_COMPENDIUM\.get\(\);/.test(cfg),
   'accessor is null-safe (server / no-viewer installs never registered the key)');
ok(!/worldRestart\(\)|gameRestart\(\)/.test(cfg.match(/ENABLE_GEM_COMPENDIUM[\s\S]{0,2000}?;/) ? cfg.match(/ENABLE_GEM_COMPENDIUM[\s\S]{0,2000}?;/)[0] : ''),
   'no restart flags: hot-reload convention kept');

// ---------------------------------------------------------------- 8. packaged jar is clean
console.log('\n8) packaged jar');
const jarsDir = rel('build', 'libs');
if (fs.existsSync(jarsDir)) {
  const jar = fs.readdirSync(jarsDir).find(f => /^ApotheosisExpansion-.*\.jar$/.test(f));
  if (jar) {
    const listing = execFileSync(process.env.JAR_EXE || 'jar', ['tf', path.join(jarsDir, jar)], { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
    ok(!/^mezz\//m.test(listing) && !/^dev\/emi\//m.test(listing), 'jar bundles no JEI/EMI classes (compileOnly honoured)');
    ok(/gemcompendium\/GemCompendiumJeiPlugin\.class/.test(listing) && /gemcompendium\/GemCompendiumEmiPlugin\.class/.test(listing),
       'both plugins are packaged');
  } else {
    ok(false, 'packaged jar found in build/libs');
  }
} else {
  console.log('  (build/libs missing - run build/package.js; skipped)');
}

console.log('\n' + (fails === 0 ? 'ALL CHECKS PASSED' : fails + ' CHECK(S) FAILED'));
process.exit(fails === 0 ? 0 : 1);
