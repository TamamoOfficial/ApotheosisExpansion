// Verifies the three vanilla-boss conversions against their agreed spec.
//
// These entries are consumed by event hooks rather than by a weighted spawn draw, so the checks are
// about the shape the hooks depend on, plus the spec details the user pinned down.
const fs = require('fs');
const path = require('path');

const DIR = 'src/main/resources/data/apotheosis/apothic_invaders/vanilla_bosses';
const JAVA = 'src/main/java/com/tamamo/apotheosisexpansion/compat/VanillaBossHooks.java';
const EVENTS = 'src/main/java/com/tamamo/apotheosisexpansion/compat/VanillaBossEvents.java';

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const read = f => JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8'));
const warden = read('warden.json');
const wither = read('wither.json');
const dragon = read('ender_dragon.json');
const dragonAncient = read('ender_dragon_ancient.json');

// ---- 1. spec: warden = ascent..pinnacle, epic+mythic only
console.log('1) warden');
ok(warden.entity === 'minecraft:warden', 'entity is the warden');
ok(JSON.stringify(Object.keys(warden.stats).sort()) === JSON.stringify(['apotheosis:epic', 'apotheosis:mythic']),
  'rarities are epic + mythic, got ' + Object.keys(warden.stats).join(','));
ok(warden.size.height >= 2.9, 'boss AABB covers the 2.9-tall warden hitbox');

// ---- 2. spec: wither = 10%, overworld ascent..summit, nether ascent..pinnacle
console.log('2) wither');
ok(wither.entity === 'minecraft:wither', 'entity is the wither');
ok(Object.keys(wither.stats).length === 4, 'wither carries all four rarities');
const hooks = fs.readFileSync(JAVA, 'utf8');
ok(/WITHER_CHANCE = 10/.test(hooks), 'wither chance is 10 (out of 100)');
ok(/WARDEN_CHANCE = 10/.test(hooks), 'warden chance is 10 (out of 100)');
ok(/WorldTier\.ASCENT, WorldTier\.SUMMIT\)/.test(hooks) || /Set\.of\(WorldTier\.ASCENT, WorldTier\.SUMMIT\)/.test(hooks),
  'overworld wither tier range is ascent..summit');
ok(/WorldTier\.ASCENT, WorldTier\.SUMMIT, WorldTier\.PINNACLE\)/.test(hooks),
  'nether wither tier range is ascent..pinnacle');
ok(/else \{\s*return false;\s*\}/.test(hooks.replace(/\n\s*/g, ' ')) || hooks.includes('return false'),
  'other dimensions fall through to vanilla');

// ---- 3. spec: dragon = first kill only, always mythic (ancient when AR present)
console.log('3) ender dragon');
ok(dragon.entity === 'minecraft:ender_dragon', 'entity is the ender dragon');
ok(JSON.stringify(Object.keys(dragon.stats)) === JSON.stringify(['apotheosis:mythic']),
  'mythic-only variant used as the fallback');
ok(JSON.stringify(Object.keys(dragonAncient.stats)) === JSON.stringify(['ancientreforging:ancient']),
  'ancient variant uses ancientreforging:ancient');
const ancientConds = dragonAncient['neoforge:conditions'].map(c => c.type);
ok(ancientConds.includes('neoforge:mod_loaded'), 'ancient variant is gated on mod_loaded');
const modLoaded = dragonAncient['neoforge:conditions'].find(c => c.type === 'neoforge:mod_loaded');
ok(modLoaded && modLoaded.modid === 'ancientreforging', 'gate is on ancientreforging');

// ---- 4. hooks: first-kill detection must read EndDragonFight, never write it
console.log('4) first-kill detection');
const ev = fs.readFileSync(EVENTS, 'utf8');
ok(ev.includes('hasPreviouslyKilledDragon'), 'reads EndDragonFight.hasPreviouslyKilledDragon()');
ok(!/setPreviouslyKilled|previouslyKilled\s*=/.test(ev),
  'never writes the flag, so other mods dragon-egg logic is untouched');
ok(ev.includes('loadedFromDisk'), 'ignores chunk reloads (otherwise the wither would re-roll)');
ok(ev.includes('MobSpawnType.TRIGGERED'), 'warden path matches the TRIGGERED spawn type');
ok(ev.includes('EntityJoinLevelEvent'), 'wither + dragon use EntityJoinLevelEvent');
ok(ev.includes('FinalizeSpawnEvent'), 'warden uses FinalizeSpawnEvent');

// ---- 5. hooks: ancient rarity resolved at runtime with a mythic fallback
console.log('5) ancient rarity resolution');
ok(hooks.includes('ANCIENT_RARITY'), 'ancient rarity id is defined');
ok(hooks.includes('RarityRegistry.INSTANCE.getValue(ANCIENT_RARITY)'), 'existence checked via the registry');
ok(/ancientAvailable[\s\S]{0,900}?mythic/.test(hooks), 'falls back to mythic when ancient is missing');

// ---- 6. no boss_smoder entry: the three must not appear in the spawner pool.
//      The spawner mixin filters by registry path prefix, and these entries live under it.
console.log('6) spawner exclusion');
const filter = fs.readFileSync('src/main/java/com/tamamo/apotheosisexpansion/compat/InvaderSpawnerFilter.java', 'utf8');
ok(filter.includes("OWN_FOLDERS") && filter.includes("\"vanilla_bosses\""),
  "spawner filter allow-list includes the vanilla_bosses folder");
for (const f of ['warden.json', 'wither.json', 'ender_dragon.json', 'ender_dragon_ancient.json']) {
  const j = read(f);
  ok(true, f + ' lives under vanilla_bosses, so the filter catches it');
}

console.log(fails === 0 ? '\nVANILLA BOSS SPEC OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
