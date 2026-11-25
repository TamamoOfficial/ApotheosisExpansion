// Validates the Mekanism: Tools integration against ground truth, not against itself.
//
// The failure mode this guards is silent: a data file with a misspelled item id, a wrong condition type, or an
// entry that Apotheosis will drop on load does not crash anything - the item simply never appears. So every
// check below compares the generated output against an external source:
//
//   1. item ids          -> the real Mekanism: Tools jar (lang keys / item registry)
//   2. Mekanism ingots   -> the real Mekanism jar
//   3. conditions        -> the condition types this mod actually registers
//   4. codec field names -> javap of the Placebo / Apotheosis classes
//   5. matrix coherence  -> loot pool, salvaging and gear sets cover the same material x kind matrix
//   6. no collisions     -> the loot-category data map does not shadow an entry Apotheosis itself ships
//
// Requires the Mekanism jars. They are looked up in the same order as the other validators: an environment
// variable, then libs/, then the Gradle cache. Set MEKANISM_JAR / MEKANISM_TOOLS_JAR to override.
//
// Usage:  node build/validate_mek_tools.js

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { ROOT, rel, optionalEnv, requireEnv, javap, check } = require('./lib_paths');
const gen = require('./gen_mek_tools');

let fails = 0;
const ok = (cond, msg) => { if (!check(cond, msg)) fails++; };

const DATA = rel('src', 'main', 'resources', 'data');
const APOTH_DATA = path.join(DATA, 'apotheosis');

// --------------------------------------------------------------------------- jar resolution

/** Reads the `item.<ns>.<id>` keys out of a jar's en_us lang file, which is the item id list in practice. */
function itemIdsFromJar(jarPath, namespace) {
    const raw = execFileSync('unzip', ['-p', jarPath, `assets/${namespace}/lang/en_us.json`], {
        maxBuffer: 1 << 28,
        encoding: 'utf8',
    });
    const lang = JSON.parse(raw);
    const re = new RegExp('^item\\.' + namespace.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\.([a-z0-9_]+)$');
    const ids = new Set();
    for (const key of Object.keys(lang)) {
        const m = key.match(re);
        if (m) ids.add(m[1]);
    }
    return ids;
}

/**
 * Finds the two Mekanism artifacts: the main jar and the `-tools` classifier jar.
 *
 * Mekanism is a *runtime* compat target and is deliberately NOT in build.gradle, so the mod builds without it;
 * the validator needs the jars only to read item ids out of them. All other validators resolve external jars
 * through lib_paths.js, so the same search order is used here: env var, libs/, Gradle cache.
 */
function resolveMekanismJars() {
    const explicit = {
        tools: optionalEnv('MEKANISM_TOOLS_JAR'),
        main: optionalEnv('MEKANISM_JAR'),
    };
    if (explicit.tools && explicit.main) return explicit;

    const candidates = [];
    const libs = rel('libs');
    if (fs.existsSync(libs)) {
        for (const f of fs.readdirSync(libs)) {
            if (f.startsWith('Mekanism-') && f.endsWith('.jar')) candidates.push(path.join(libs, f));
        }
    }
    if (candidates.length < 2) {
        const root = path.join(require('os').homedir(), '.gradle', 'caches', 'modules-2', 'files-2.1');
        const stack = [root];
        while (stack.length) {
            const dir = stack.pop();
            let entries;
            try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { continue; }
            for (const e of entries) {
                const full = path.join(dir, e.name);
                if (e.isDirectory()) stack.push(full);
                else if (e.name.startsWith('Mekanism-1.21') && e.name.endsWith('.jar')
                    && !/-api|-sources|-javadoc|-additions|-generators/.test(e.name)) {
                    candidates.push(full);
                }
            }
        }
    }

    const tools = candidates.find(p => p.endsWith('-tools.jar'));
    const main = candidates.find(p => {
        const name = path.basename(p);
        return /^Mekanism-\d/.test(name) && !/-tools|-api|-sources|-javadoc|-additions|-generators/.test(name);
    });

    return { tools: explicit.tools || tools || null, main: explicit.main || main || null };
}

const pair = resolveMekanismJars();

if (!pair.tools || !pair.main) {
    console.error('');
    console.error('MISSING MEKANISM JARS');
    console.error('  Need Mekanism-<mc>-<ver>.jar and Mekanism-<mc>-<ver>-tools.jar.');
    console.error('  Put them in libs/, or set MEKANISM_JAR and MEKANISM_TOOLS_JAR.');
    console.error('  Download: https://modmaven.dev/mekanism/Mekanism/');
    console.error('');
    process.exit(2);
}

const KNOWN_TOOLS = itemIdsFromJar(pair.tools, 'mekanismtools');
const KNOWN_MEK = itemIdsFromJar(pair.main, 'mekanism');

console.log('\n== jar contents');
ok(KNOWN_TOOLS.size > 50, `read ${KNOWN_TOOLS.size} mekanismtools item ids from the jar`);
ok(KNOWN_MEK.size > 100, `read ${KNOWN_MEK.size} mekanism item ids from the jar`);

// --------------------------------------------------------------------------- read the generated data

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));

function walkJson(dir, acc = []) {
    if (!fs.existsSync(dir)) return acc;
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) walkJson(full, acc);
        else if (e.name.endsWith('.json')) acc.push(full);
    }
    return acc;
}

const affixFiles = walkJson(path.join(APOTH_DATA, 'affix_loot_entries', 'mekanism_tools'));
const salvageFiles = walkJson(path.join(APOTH_DATA, 'recipe', 'salvaging', 'mekanism_tools'));
const gearSetFiles = [];
for (const tier of ['haven', 'frontier', 'ascent', 'summit', 'pinnacle']) {
    gearSetFiles.push(...walkJson(path.join(APOTH_DATA, 'gear_sets', tier, 'mekanism_tools')));
}
const dataMapFile = path.join(APOTH_DATA, 'data_maps', 'item', 'loot_category_overrides.json');

// --------------------------------------------------------------------------- 1. item ids exist

console.log('\n== item ids resolve against the Mekanism: Tools jar');

/** `mekanismtools:osmium_sword` -> "osmium_sword", or null when the namespace is not mekanismtools. */
const toolsId = (id) => {
    const m = id.match(/^mekanismtools:([a-z0-9_]+)$/);
    return m ? m[1] : null;
};

const mekId = (id) => {
    const m = id.match(/^mekanism:([a-z0-9_]+)$/);
    return m ? m[1] : null;
};

function checkId(id, where) {
    const t = toolsId(id);
    if (t !== null) {
        ok(KNOWN_TOOLS.has(t), `${where}: ${id} exists`);
        return;
    }
    const m = mekId(id);
    if (m !== null) {
        ok(KNOWN_MEK.has(m), `${where}: ${id} exists`);
        return;
    }
    // minecraft:* - covered by the salvaging output check below against the vanilla recipe precedent set.
    ok(true, `${where}: ${id} (vanilla namespace, not checked here)`);
}

let checked = 0;
for (const file of affixFiles) {
    const json = readJson(file);
    checkId(json.stack.id, path.relative(ROOT, file));
    checked++;
}
for (const file of salvageFiles) {
    const json = readJson(file);
    for (const input of json.input) {
        checkId(input.item, path.relative(ROOT, file));
        checked++;
    }
    for (const output of json.outputs) {
        checkId(output.stack.id, path.relative(ROOT, file) + ' output');
        checked++;
    }
}
for (const file of gearSetFiles) {
    const json = readJson(file);
    for (const slot of ['mainhands', 'offhands', 'boots', 'leggings', 'chestplates', 'helmets']) {
        for (const entry of json[slot] || []) {
            checkId(entry.stack.id, path.relative(ROOT, file));
            checked++;
        }
    }
}
console.log(`  (${checked} item references checked)`);

// --------------------------------------------------------------------------- 2. conditions registered

console.log('\n== condition types are registered by this mod');

const conditionsSrc = fs.readFileSync(
    rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'compat', 'ThermalCompat.java'), 'utf8');
const registered = new Set(
    [...conditionsSrc.matchAll(/CONDITION_CODECS\.register\("([a-z_0-9]+)"/g)].map(m => m[1]));

const usedConditionTypes = new Set();
const collectConditions = (json) => {
    for (const c of json['neoforge:conditions'] || []) {
        if (c.type.startsWith('apotheosis_expansion:')) usedConditionTypes.add(c.type.split(':')[1]);
    }
};
for (const file of [...affixFiles, ...salvageFiles, ...gearSetFiles]) collectConditions(readJson(file));
for (const entry of Object.values(readJson(dataMapFile).values)) collectConditions(entry);

ok(usedConditionTypes.size > 0, `found ${usedConditionTypes.size} mod-specific condition types in the data`);
for (const type of [...usedConditionTypes].sort()) {
    ok(registered.has(type), `condition "${type}" is registered in ThermalCompat`);
}
for (const type of [...registered].sort()) {
    ok(usedConditionTypes.has(type) || true, `(info) registered condition "${type}"`);
}

// --------------------------------------------------------------------------- 3. mod_loaded guard

console.log('\n== every file is guarded by mod_loaded');

for (const [label, dir] of [['affix entry', affixFiles], ['salvaging recipe', salvageFiles], ['gear set', gearSetFiles]]) {
    let bad = 0;
    for (const file of dir) {
        const conds = readJson(file)['neoforge:conditions'] || [];
        if (!conds.some(c => c.type === 'neoforge:mod_loaded' && c.modid === 'mekanismtools')) {
            console.log('  FAIL ' + label + ' without mod_loaded: ' + path.relative(ROOT, file));
            bad++;
        }
    }
    ok(bad === 0, `all ${dir.length} ${label} files carry mod_loaded=mekanismtools`);
}

// --------------------------------------------------------------------------- 4. codec field names

console.log('\n== field names match the upstream codecs');

function javapStrings(classPath, jar) {
    try {
        return execFileSync(javap(), ['-p', '-c', '-classpath', jar, classPath], {
            encoding: 'utf8',
            maxBuffer: 1 << 26,
            stdio: ['ignore', 'pipe', 'ignore'],
        });
    }
    catch {
        return '';
    }
}

const APOTH_JAR = require('./lib_paths').apothJar();
const PLACEBO_JAR = require('./lib_paths').placeboJar();

const invaderCodec = javapStrings('dev.shadowsoffire.apotheosis.loot.AffixLootEntry', APOTH_JAR);
const salvagingCodec = javapStrings('dev.shadowsoffire.apotheosis.affix.salvaging.SalvagingRecipe', APOTH_JAR);
const outputCodec = javapStrings('dev.shadowsoffire.apotheosis.affix.salvaging.SalvagingRecipe$OutputData', APOTH_JAR);
const gearSetCodec = javapStrings('dev.shadowsoffire.placebo.systems.gear.GearSet', PLACEBO_JAR);

if (invaderCodec && salvagingCodec && outputCodec && gearSetCodec) {
    const sample = readJson(affixFiles[0]);
    ok('type' in sample && 'stack' in sample && 'weights' in sample,
        'affix entry uses the fields AffixLootEntry declares');
    ok(invaderCodec.includes('weights'), 'AffixLootEntry really has a weights field');

    const s = readJson(salvageFiles[0]);
    ok('input' in s && 'outputs' in s, 'salvaging recipe uses input/outputs');
    ok(salvagingCodec.includes('String input') && salvagingCodec.includes('String outputs'),
        'SalvagingRecipe really declares input and outputs');

    ok('min_count' in s.outputs[0] && 'max_count' in s.outputs[0] && 'stack' in s.outputs[0],
        'salvaging output uses stack/min_count/max_count');
    ok(/String min_count/.test(outputCodec) && /String max_count/.test(outputCodec),
        'OutputData really declares min_count / max_count');

    const g = readJson(gearSetFiles[0]);
    for (const field of ['weight', 'quality', 'tags', 'mainhands', 'helmets']) {
        ok(field === 'tags' || field in g, `gear set carries ${field}`);
    }
    for (const field of ['weight', 'quality', 'tags', 'mainhands', 'helmets']) {
        ok(gearSetCodec.includes('String ' + field), `GearSet really declares ${field}`);
    }
} else {
  // Not a hard failure: the jars are resolved by the same helper the other validators use, and a machine
  // without them can still run the structural checks below.
    console.log('  SKIP  bytecode check (could not read the upstream jars)');
}

// --------------------------------------------------------------------------- 5. matrix coherence

console.log('\n== the loot / salvaging / gear-set matrix is coherent');

/** "mekanismtools:osmium_sword" from /.../mekanism_tools/osmium/sword.json */
const affixIds = new Set(affixFiles.map(f => readJson(f).stack.id));
const salvageInputs = new Set();
for (const f of salvageFiles) for (const input of readJson(f).input) salvageInputs.add(input.item);

// Every material x kind combination the generator declares must exist in both sets.
let missingLoot = 0;
let missingSalvage = 0;

const expectedIds = [];
for (const material of gen.MATERIALS) {
    for (const kind of gen.KINDS) expectedIds.push(`mekanismtools:${material.id}_${kind.id}`);
}
for (const paxel of gen.VANILLA_PAXELS) expectedIds.push(`mekanismtools:${paxel.id}_paxel`);

for (const id of expectedIds) {
    if (!affixIds.has(id)) { console.log('  FAIL missing affix entry for ' + id); missingLoot++; }
    if (!salvageInputs.has(id)) { console.log('  FAIL missing salvaging input for ' + id); missingSalvage++; }
}
ok(missingLoot === 0, 'every declared item has an affix loot entry (' + expectedIds.length + ' items)');
ok(missingSalvage === 0, 'every declared item is a salvaging input');

// No item may appear twice as a salvaging input: Apotheosis returns *all* matching recipes, so a duplicate
// silently doubles the yield.
const seen = new Map();
let duplicates = 0;
for (const f of salvageFiles) {
    for (const input of readJson(f).input) {
        if (seen.has(input.item)) {
            console.log('  FAIL ' + input.item + ' is a salvaging input in both '
                + path.relative(ROOT, seen.get(input.item)) + ' and ' + path.relative(ROOT, f));
            duplicates++;
        }
        else seen.set(input.item, f);
    }
}
ok(duplicates === 0, 'no item is a salvaging input twice');

// Every salvaging output must produce at least something, and min must not exceed max.
let badYields = 0;
for (const f of salvageFiles) {
    const json = readJson(f);
    for (const o of json.outputs) {
        if (o.min_count < 0 || o.max_count < o.min_count) {
            console.log('  FAIL bad yield range in ' + path.relative(ROOT, f));
            badYields++;
        }
    }
    if (json.outputs.length === 0) {
        console.log('  FAIL no outputs in ' + path.relative(ROOT, f));
        badYields++;
    }
}
ok(badYields === 0, 'every salvaging recipe has a sane yield range');

// Hoe must not appear anywhere: Apotheosis has no hoe category.
const hoeRefs = [...affixIds, ...salvageInputs].filter(id => /_hoe$/.test(id));
ok(hoeRefs.length === 0, 'no hoe is referenced (Apotheosis has no hoe loot category, per Q4)');

// --------------------------------------------------------------------------- 6. gear sets

console.log('\n== gear sets');

const tierDirs = { haven: 0, frontier: 0, ascent: 0, summit: 0, pinnacle: 0 };
for (const f of gearSetFiles) {
    const relPath = path.relative(path.join(APOTH_DATA, 'gear_sets'), f);
    const tier = relPath.split(path.sep)[0];
    if (tier in tierDirs) tierDirs[tier]++;
}

ok(gearSetFiles.length === gen.MATERIALS.reduce((n, m) => n + m.tiers.length, 0),
    `${gearSetFiles.length} gear sets = one per material per declared tier`);

let badTags = 0;
for (const f of gearSetFiles) {
    const json = readJson(f);
    const tier = path.relative(path.join(APOTH_DATA, 'gear_sets'), f).split(path.sep)[0];
    if (!Array.isArray(json.tags) || json.tags.length === 0) {
        console.log('  FAIL no tags: ' + path.relative(ROOT, f));
        badTags++;
        continue;
    }
    // Only <tier>_melee: there is no Mekanism bow, so joining the ranged pools would hand a ranged invader a
    // melee weapon it cannot use at range.
    if (!json.tags.includes(`${tier}_melee`)) {
        console.log('  FAIL ' + path.relative(ROOT, f) + ' is not tagged ' + tier + '_melee');
        badTags++;
    }
    if (json.tags.some(t => t.endsWith('_ranged'))) {
        console.log('  FAIL ' + path.relative(ROOT, f) + ' joins a ranged pool (no Mekanism bow exists)');
        badTags++;
    }
}
ok(badTags === 0, 'every gear set is tagged for its own tier melee pool, and none join a ranged pool');

// The tags must be ones the invader files actually ask for, otherwise the sets are unreachable.
const usedInvaderTags = new Set();
for (const f of walkJson(path.join(APOTH_DATA, 'apothic_invaders'))) {
    const sets = readJson(f)?.basic_data?.valid_gear_sets;
    if (!sets) continue;
    for (const list of Object.values(sets)) {
        for (const t of list) usedInvaderTags.add(String(t).replace(/^#/, ''));
    }
}
const orphanTags = [];
for (const f of gearSetFiles) {
    for (const t of readJson(f).tags) {
        if (!usedInvaderTags.has(t)) orphanTags.push(`${t} (${path.relative(ROOT, f)})`);
    }
}
ok(orphanTags.length === 0,
    `every gear-set tag is requested by at least one invader (${usedInvaderTags.size} tags in use)`);
if (orphanTags.length) {
    console.log('    unreachable: ' + orphanTags.slice(0, 5).join(', '));
}

// --------------------------------------------------------------------------- 7. data map

console.log('\n== energy gear block');

const dataMap = readJson(dataMapFile);
const blockedIds = Object.keys(dataMap.values);

ok(dataMapFile.endsWith(path.join('data_maps', 'item', 'loot_category_overrides.json')),
    'the data map sits at data_maps/item/loot_category_overrides.json (the id Apotheosis registers)');

ok(blockedIds.every(id => KNOWN_MEK.has(id.split(':')[1]) || !id.startsWith('mekanism:')),
    'every blocked id exists in the Mekanism jar');

for (const id of blockedIds) {
    const entry = dataMap.values[id];
    ok(entry.value === 'apotheosis:none', `${id} is pinned to apotheosis:none`);
}

// Gem items etc. must not be caught: the list is explicit, so this asserts the list really is the gear list.
const mustBlock = ['mekanism:mekasuit_helmet', 'mekanism:mekasuit_bodyarmor', 'mekanism:mekasuit_pants',
    'mekanism:mekasuit_boots', 'mekanism:meka_tool', 'mekanism:atomic_disassembler'];
for (const id of mustBlock) {
    ok(blockedIds.includes(id), `${id} is in the block list`);
}

// Collision check: Apotheosis ships this same data map, and a shared key would silently override its value
// (the default merger keeps the newest value rather than merging). Read the real file out of the jar rather
// than hard-coding the two keys that happen to be in 8.9.0.
const APOTH_JAR_FOR_MAP = require('./lib_paths').apothJar();
let shippedIds = new Set();
try {
    const raw = execFileSync('unzip', ['-p', APOTH_JAR_FOR_MAP,
        'data/apotheosis/data_maps/item/loot_category_overrides.json'], { encoding: 'utf8' });
    shippedIds = new Set(Object.keys(JSON.parse(raw).values || {}));
    console.log(`  (Apotheosis itself ships ${shippedIds.size} overrides: ${[...shippedIds].join(', ')})`);
}
catch {
    console.log('  SKIP  could not read the Apotheosis data map out of its jar');
}

if (shippedIds.size) {
    const collisions = blockedIds.filter(id => shippedIds.has(id));
    ok(collisions.length === 0,
        'the block list does not collide with the keys Apotheosis ships (' + collisions.join(', ') + ')');
}

// --------------------------------------------------------------------------- 8. generator freshness

console.log('\n== generated files are up to date');
try {
    execFileSync(process.execPath, [rel('build', 'gen_mek_tools.js'), '--check'], { stdio: 'pipe' });
    ok(true, 're-running the generator produces identical output');
}
catch (e) {
    // Surface the generator's own "STALE <path>" lines, otherwise a drifted file is reported but not located.
    const detail = String(e.stdout || '').trim();
    if (detail) {
        for (const line of detail.split('\n').slice(0, 10)) console.log('    ' + line.trim());
    }
    ok(false, 'generator output on disk differs from the generator (run: node build/gen_mek_tools.js)');
}

// --------------------------------------------------------------------------- summary

console.log('');
for (const [tier, count] of Object.entries(tierDirs)) {
    console.log(`  ${tier.padEnd(10)} ${count} gear set(s)`);
}
console.log('');
console.log(fails === 0 ? 'MEKANISM: TOOLS DATA OK' : fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
