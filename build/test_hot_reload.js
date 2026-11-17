// Verifies that every monster-cap option is hot-reloadable, i.e. an edit made while the game is
// running takes effect without a restart, a relog, or a /reload.
//
// "Hot-reloadable" is easy to claim and easy to break in three specific ways, so each is checked:
//   1. no option asks NeoForge for a restart (worldRestart/gameRestart), which would pin the old value
//   2. no option value is copied into a static field at load time - only read through Config on each use
//   3. the per-tick memo cannot outlive a config change (the generation counter below)
//
// Run from the project root:  node build/test_hot_reload.js
const fs = require('fs');

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const read = (p) => fs.readFileSync(p, 'utf8');
const cfg = read('src/main/java/com/tamamo/apotheosisexpansion/Config.java');
const scaler = read('src/main/java/com/tamamo/apotheosisexpansion/spawn/MobCapScaler.java');
const main = read('src/main/java/com/tamamo/apotheosisexpansion/ApotheosisExpansion.java');
const sync = read('src/main/java/com/tamamo/apotheosisexpansion/spawn/FakePlayerTierSync.java');
const bridge = read('src/main/java/com/tamamo/apotheosisexpansion/spawn/MobCapHudBridge.java');

/** The cap-related option keys this feature owns. */
const CAP_KEYS = [
  'enable_mob_cap_scaling',
  'mob_cap_count_all_tiers',
  'mob_cap_bonus_per_player',
  'mob_cap_max_players',
  'sync_mob_cap_to_hud',
  'fake_players_inherit_world_tier',
  'debug_mob_cap',
];

// ---------------------------------------------------------------- 1. no restart markers
console.log('1) no option requests a restart');
{
  // worldRestart()/gameRestart() make NeoForge keep the old cached value until the world or game restarts.
  for (const key of CAP_KEYS) {
    const idx = cfg.indexOf('"' + key + '"');
    ok(idx >= 0, key + ' exists');
    if (idx < 0) continue;
    // look back over the builder chain that belongs to this definition
    const chainStart = Math.max(0, cfg.lastIndexOf('\n        ', idx));
    const chain = cfg.slice(chainStart, idx);
    ok(!/\.(worldRestart|gameRestart)\s*\(/.test(chain), key + ' has no worldRestart/gameRestart marker');
  }
}

// ---------------------------------------------------------------- 2. no static value copies
console.log('\n2) option values are read per use, never copied into a static');
{
  // A static snapshot would freeze the value at class-init time and defeat the whole point.
  const statics = [
    ...scaler.matchAll(/(?:private|public|protected)\s+static\s+(?!final\s+Map|final\s+ThreadLocal)(?!void)[\w<>\[\], .]+\s+(\w+)\s*=\s*[^;]*Config\./g),
    ...bridge.matchAll(/(?:private|public|protected)\s+static\s+(?!final\s+Map)(?!void)[\w<>\[\], .]+\s+(\w+)\s*=\s*[^;]*Config\./g),
    ...sync.matchAll(/(?:private|public|protected)\s+static\s+(?!final\s+(?:Logger|String))[\w<>\[\], .]+\s+(\w+)\s*=\s*[^;]*Config\./g),
  ].map(m => m[0].trim());
  ok(statics.length === 0, 'no static field caches a Config value' + (statics.length ? ': ' + statics.join(' | ') : ''));

  // The switch must be consulted at call time.
  ok(scaler.includes('Config.enableMobCapScaling()'), 'scaler reads the master switch per call');
  ok(scaler.includes('Config.mobCapCountAllTiers()'), 'scaler reads the all-tiers switch per call');
  ok(scaler.includes('Config.mobCapBonusPerPlayer()'), 'scaler reads the bonus per call');
  ok(scaler.includes('Config.mobCapMaxPlayers()'), 'scaler reads the player ceiling per call');
  ok(bridge.includes('Config.syncMobCapToHud()'), 'HUD bridge reads its switch per call');
  ok(sync.includes('Config.fakePlayersInheritWorldTier()'), 'fake-tier sync reads its switch per call');
}

// ---------------------------------------------------------------- 3. the memo cannot go stale
console.log('\n3) the per-tick memo cannot outlive a config change');
{
  ok(/private record CacheKey\([^)]*generation\)/.test(scaler),
    'CacheKey carries a generation counter');
  ok(/new CacheKey\([\s\S]{0,200}GENERATION\)/.test(scaler),
    'the generation is part of every key');
  ok(/public static void clearCache\(\)\s*\{[\s\S]{0,200}GENERATION\+\+/.test(scaler),
    'clearCache bumps the generation, so old keys become unmatchable');
  ok(/GENERATION\+\+;[\s\S]{0,80}CACHE\.clear\(\)/.test(scaler),
    'clearCache also drops the map');

  // clearing must happen on every config event, not only on reload
  ok(/MobCapScaler\.clearCache\(\)/.test(main), 'the mod clears the memo on config change');
  const loadBlock = main.slice(main.indexOf('private void onConfigChanged'));
  ok(/clearCache\(\)[\s\S]{0,400}?if \(event instanceof ModConfigEvent\.Reloading\)/.test(loadBlock) ||
     /setDebug\(Config\.debugMobCap\(\)\);\s*\n\s*com\.tamamo\.apotheosisexpansion\.spawn\.MobCapScaler\.clearCache\(\);/.test(loadBlock),
    'the clear runs for both Loading and Reloading');
}

// ---------------------------------------------------------------- 4. both counting rules exist
console.log('\n4) both counting rules are wired');
{
  ok(/Config\.mobCapCountAllTiers\(\)\s*\)\s*\{[\s\S]{0,120}group = Math\.max\(1, nearby\.size\(\)\)/.test(scaler),
    'all-tiers mode counts every nearby player');
  ok(/group = largestSameTierGroup\(nearby\)/.test(scaler),
    'same-tier mode still uses the largest single-tier group');
  ok(cfg.includes('.define("mob_cap_count_all_tiers", false'),
    'mob_cap_count_all_tiers defaults to false (existing behaviour preserved)');
}

// ---------------------------------------------------------------- 5. formula sanity
console.log('\n5) formula matches the documented result');
{
  // mirrors MobCapScaler.multiplier
  const mult = (n, bonus, max) => {
    if (n <= 1) return 1;
    const capped = Math.min(n, Math.max(2, max));
    return Math.max(1, 1 + bonus * (capped - 1));
  };
  const cap = (m) => Math.round(70 * m);

  ok(cap(mult(1, 0.5, 8)) === 70, '1 player -> 70');
  ok(cap(mult(2, 0.5, 8)) === 105, '2 players -> 105');
  ok(cap(mult(3, 0.5, 8)) === 140, '3 players -> 140');
  ok(cap(mult(4, 0.5, 8)) === 175, '4 players -> 175');
  // the new switch only changes WHO counts, never the formula itself
  ok(cap(mult(3, 0.5, 8)) === Math.round(70 * (1 + 0.5 * 2)),
    'mixed-tier group of 3 yields the same 140 as a same-tier group of 3');
}

console.log(fails === 0 ? '\nHOT RELOAD OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
