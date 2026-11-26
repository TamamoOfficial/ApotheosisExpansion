// Statically verifies the two-part HUD story:
//
//   A. Apotheosis Expansion ships a multi-row hunger bar (FoodHudLayer) that wraps, rather than replaces, the
//      vanilla FOOD_LEVEL layer - so a player with the default ceiling sees byte-for-byte vanilla rendering.
//   B. Classic Bar reads the same max_food attribute by id (no compile dependency) and divides by it instead of
//      the literal 20, so its single 77px bar stays proportional.
//
// Checks, in order of how easily they break silently:
//   1. the wrapped layer id still matches VanillaGuiLayers.FOOD_LEVEL (otherwise nothing is wrapped)
//   2. FoodHudLayer delegates to the original layer at the vanilla ceiling (the no-regression guarantee)
//   3. the icon arithmetic reproduces vanilla's comparison for the first ten icons exactly
//   4. rows are sized by the ceiling, and rightHeight grows per row (air bar / item name stacking)
//   5. the sprites are vanilla sprite ids, not a texture this mod would have to ship
//   6. the mixin/HUD wiring is client-only (a dedicated server never resolves client classes)
//   7. Classic Bar resolves the attribute by resource id and tolerates null
//   8. Classic Bar's Hunger no longer contains a literal 20 wherever the ceiling belongs
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { mcJar, javap, rel } = require('./lib_paths');

const JAVA = javap();
const MC = mcJar();

let fails = 0;
const pass = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; return c; };

const APOTH_ROOT = rel('.');
// Sibling checkout next to the workspace folder, not inside it.
const CB_ROOT = path.resolve(APOTH_ROOT, '..', '..', 'ClassicBar-port', 'cb121');

const read = (p) => fs.readFileSync(p, 'utf8');
const exists = (p) => fs.existsSync(p);

const runJavap = (...args) =>
    execFileSync(JAVA, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

// ---------------------------------------------------------------- 1. layer id
console.log('1) the wrapped layer id');
const vanillaLayers = runJavap('-p', '-c', '-classpath', MC, 'net.neoforged.neoforge.client.gui.VanillaGuiLayers');
pass(/FOOD_LEVEL = ResourceLocation/.test(vanillaLayers) || /food_level/.test(vanillaLayers),
    'VanillaGuiLayers.FOOD_LEVEL still resolves to "food_level"');

const hudSrc = read(rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'client', 'FoodHudIntegration.java'));
pass(hudSrc.includes('VanillaGuiLayers.FOOD_LEVEL'), 'FoodHudIntegration targets VanillaGuiLayers.FOOD_LEVEL');
pass(hudSrc.includes('wrapLayer'), 'uses wrapLayer (keeps the id, so same-id HUD mods still match)');
pass(!hudSrc.includes('replaceLayer'), 'does not use replaceLayer (would lose the delegate-to-vanilla path)');

// ---------------------------------------------------------------- 2. delegation
console.log('2) the no-regression guarantee');
const layerSrc = read(rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'client', 'FoodHudLayer.java'));
pass(/this\.vanilla\.render\(graphics, partialTick\)/.test(layerSrc),
    'delegates to the wrapped vanilla layer');
pass(/maxFood\(player\)\s*<=\s*ModAttributes\.VANILLA_MAX_FOOD/.test(layerSrc),
    'delegates whenever the ceiling is at or below the vanilla value');

// ---------------------------------------------------------------- 3. icon arithmetic
console.log('3) icon arithmetic matches vanilla for the first ten icons');
const gui = runJavap('-p', '-c', '-classpath', MC, 'net.minecraft.client.gui.Gui');
const foodBody = gui.slice(gui.indexOf('private void renderFood('), gui.indexOf('private void renderVehicleHealth('));
pass(/iconst_2/.test(foodBody) || /bipush\s+2/.test(foodBody), 'vanilla multiplies the icon index by 2');
pass(/bipush\s+10/.test(foodBody) || /bipush\s+10\n.*100/.test(foodBody),
    'vanilla hard-codes 10 icons per row');
// The two comparisons the layer must reproduce: `j*2+1 < i` (full) and `j*2+1 == i` (half).
pass(/covered \+ 1 < foodLevel/.test(layerSrc), 'full-icon test is j*2+1 < foodLevel (same as vanilla)');
pass(/covered \+ 1 == foodLevel/.test(layerSrc), 'half-icon test is j*2+1 == foodLevel (same as vanilla)');

// ---------------------------------------------------------------- 4. rows and rightHeight
console.log('4) multi-row layout and the height counter');
pass(/\(maxFood \+ 1\) \/ 2/.test(layerSrc),
    'icon count is derived from the ceiling (ceil(maxFood/2) icons)');
pass(/gui\.rightHeight \+= rows \* ROW_HEIGHT/.test(layerSrc),
    'rightHeight grows by one row per 10 icons, so air and item name stack above the bar');
pass(/gui\.rightHeight/.test(layerSrc) && !/leftHeight/.test(layerSrc),
    'only the right (food) side counter is touched - the left side belongs to hearts/armor');

// ---------------------------------------------------------------- 5. sprites
console.log('5) sprites');
for (const sprite of ['hud/food_empty', 'hud/food_half', 'hud/food_full',
    'hud/food_empty_hunger', 'hud/food_half_hunger', 'hud/food_full_hunger']) {
    pass(layerSrc.includes('"' + sprite + '"'), 'uses vanilla sprite id ' + sprite);
}
// and they must really exist in the client resources jar, or blitSprite renders the missing-texture sprite.
// The sprites live in the "-client-extra-aka-minecraft-resources" artifact, not in the merged classes jar:
// the merged jar carries net/minecraft classes plus NeoForge patches and no assets at all.
// `unzip -l` on these multi-megabyte archives overruns Node's spawn buffer (ENOBUFS), so names are streamed
// through `-Z1` with an explicit maxBuffer.
const artifactDir = path.dirname(MC);
const clientExtra = fs.readdirSync(artifactDir).find(f => /-client-extra-aka-minecraft-resources\.jar$/.test(f));
pass(!!clientExtra, 'the client resources artifact is present next to the merged jar');
const hudSprites = new Set(
    execFileSync('unzip', ['-Z1', path.join(artifactDir, clientExtra)], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })
        .split('\n')
        .map(l => l.trim())
        .filter(n => n.includes('/hud/') && n.endsWith('.png')));
pass(hudSprites.size > 0, 'the hud sprite directory is present (' + hudSprites.size + ' sprites)');
for (const sprite of ['food_empty', 'food_half', 'food_full']) {
    pass([...hudSprites].some(n => n.endsWith('/hud/' + sprite + '.png')),
        'sprite ' + sprite + ' exists as a vanilla sprite id');
}

// ---------------------------------------------------------------- 6. client-only wiring
console.log('6) client-only wiring');
const mainSrc = read(rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'ApotheosisExpansion.java'));
const clientBlock = mainSrc.slice(mainSrc.indexOf('FMLEnvironment.dist.isClient()'));
pass(clientBlock.includes('FoodHudIntegration.register(modBus)'),
    'the HUD layer is registered inside the isClient() block');
pass(!/(?<!\/\/ )import net\.minecraft\.client/.test(mainSrc.slice(0, mainSrc.indexOf('FMLEnvironment.dist.isClient()'))),
    'the shared part of the mod constructor imports no client class');

// ---------------------------------------------------------------- 7. Classic Bar side
console.log('7) Classic Bar compatibility class');
if (!exists(CB_ROOT)) {
    console.log('  SKIP Classic Bar checkout not found at ' + CB_ROOT);
}
else {
    const capacityPath = path.join(CB_ROOT, 'src', 'main', 'java', 'tfar', 'classicbar', 'compat', 'HungerCapacity.java');
    pass(exists(capacityPath), 'HungerCapacity exists');
    const capacity = read(capacityPath);

    pass(capacity.includes('apotheosis_expansion') && capacity.includes('max_food'),
        'resolves the attribute by id "apotheosis_expansion:max_food"');
    pass(/BuiltInRegistries\.ATTRIBUTE\.getHolder/.test(capacity),
        'looks the attribute up in the registry (no compile dependency on this mod)');
    pass(!/import com\.tamamo/.test(capacity),
        'imports nothing from Apotheosis Expansion - the companion stays optional');
    pass(/instance == null/.test(capacity) && /VANILLA_MAX_FOOD/.test(capacity),
        'tolerates a null AttributeInstance instead of reading it (getAttributeValue would throw)');
    pass(/!\s*\(value >= 1\.0D\)/.test(capacity),
        'rejects NaN / non-positive values before they reach a division');

    // ------------------------------------------------------------ 8. no stray literal 20
    console.log('8) Classic Bar Hunger has no literal 20 where the ceiling belongs');
    const hungerPath = path.join(CB_ROOT, 'src', 'main', 'java', 'tfar', 'classicbar', 'impl', 'overlays', 'vanilla', 'Hunger.java');
    const hunger = read(hungerPath);
    // Strip comments and strings, then look for `= 20` / `20;` assignments that would be a missed ceiling.
    const code = hunger
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/\/\/[^\n]*/g, '');
    const strayTwenty = code.split('\n')
        .map((line, i) => ({ line: line.trim(), no: i + 1 }))
        .filter(o => /\b(maxHunger|maxSat|maxFood)\s*=\s*20\b/.test(o.line));
    pass(strayTwenty.length === 0,
        'no `max* = 20` left in Hunger.java' + (strayTwenty.length ? ' (line ' + strayTwenty.map(o => o.no).join(', ') + ')' : ''));
    pass(/HungerCapacity\.maxFood\(player\)/.test(code),
        'Hunger reads the ceiling through HungerCapacity.maxFood(player)');
    const capacityCalls = (code.match(/HungerCapacity\.maxFood\(player\)/g) || []).length;
    pass(capacityCalls >= 3,
        'every ceiling use goes through the helper (found ' + capacityCalls + ', expected 3: renderBar + 2 width methods)');

    // the built jar must actually contain the new class
    const cbJar = path.join(CB_ROOT, 'build', 'libs', 'ClassicBar-3.0.0-1.21.1.jar');
    if (exists(cbJar)) {
        const listing = execFileSync('unzip', ['-l', cbJar], { encoding: 'utf8' });
        pass(listing.includes('tfar/classicbar/compat/HungerCapacity.class'),
            'built Classic Bar jar contains HungerCapacity');
    }
    else {
        console.log('  SKIP Classic Bar jar not built yet (./gradlew build)');
    }
}

console.log('\n' + (fails === 0 ? 'HUD COMPAT OK' : fails + ' CHECK(S) FAILED'));
process.exit(fails === 0 ? 0 : 1);
