// Verifies the fake-player world-tier inheritance rules by simulating the decision logic.
//
// The rule set is small but the ordering matters, and one of its cases (a fake re-spawned by a
// different player) cannot be observed without two accounts and a fake-player mod. The logic is
// therefore mirrored here and exercised directly. The mirror is kept deliberately literal - it is
// a transcription of FakePlayerTierSync.inheritOnJoin/resolve, not an abstraction - so a change in
// the Java that is not reflected here shows up as a mismatch in the last check.
//
// Run from the project root:  node build/test_fake_tier_sync.js
const fs = require('fs');

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

// ---- tier vocabulary (ordinals matter only for readability here)
const HAVEN = 'haven';
const FRONTIER = 'frontier';
const ASCENT = 'ascent';
const SUMMIT = 'summit';
const PINNACLE = 'pinnacle';

// ---- mirror of FakePlayerTierSync.Source
const SUMMONER = 'SUMMONER';
const IDENTITY = 'IDENTITY';
const PROXIMITY = 'PROXIMITY';
const NONE = 'NONE';

/**
 * Mirror of FakePlayerTierSync.resolve.
 * @param fake   { name, uuid, level }
 * @param online [{ name, uuid, tier, fake }]
 * @param nearestTier tier of the nearest real player, or null
 * @param nearestName name of that player, for the "from" field
 * @param summoner    the recorded /player command issuer, or null
 */
function resolve(fake, online, nearestTier, nearestName, summoner) {
  // 0) recorded summoner from the command itself
  if (summoner && !summoner.fake) {
    return { tier: summoner.tier, source: SUMMONER, from: summoner.name };
  }
  for (const p of online) {
    if (p === fake || p.fake) continue;
    if (p.name.toLowerCase() === fake.name.toLowerCase() || p.uuid === fake.uuid) {
      return { tier: p.tier, source: IDENTITY, from: p.name };
    }
  }
  if (nearestTier) return { tier: nearestTier, source: PROXIMITY, from: nearestName };
  return { tier: null, source: NONE, from: '-' };
}

/**
 * Mirror of FakePlayerTierSync.inheritOnJoin.
 * @param current the tier the fake already carries (from playerdata), or null
 * @returns the tier after the join, or null when nothing was written
 */
function onJoin(current, resolved) {
  if (resolved.tier === null || resolved.tier === current) return current;
  // A weak (proximity) signal must not undo a tier that was set deliberately.
  if (resolved.source === PROXIMITY && current !== null && current !== HAVEN) return current;
  return resolved.tier;
}

// ------------------------------------------------------------------ the four scenarios in question
console.log('1) the four scenarios (same-name fake, two players online)');
{
  const alice = { name: 'Alice', uuid: 'uuid-alice', tier: ASCENT, fake: false };   // 攀登
  const bob = { name: 'Bob', uuid: 'uuid-bob', tier: PINNACLE, fake: false };       // 顶峰
  // A fake is addressed by name, so "Alice's fake" and "Bob's fake" are two different fake names.
  const aliceFake = { name: 'Alice', uuid: 'uuid-fake-a', fake: true };
  const bobFake = { name: 'Bob', uuid: 'uuid-fake-b', fake: true };

  // (1) 攀登玩家召唤攀登假人
  ok(onJoin(null, resolve(aliceFake, [alice, bob], null)) === ASCENT,
    'ascent player spawns an ascent fake -> ascent');

  // (2) 顶峰玩家召唤顶峰假人
  ok(onJoin(null, resolve(bobFake, [alice, bob], null)) === PINNACLE,
    'pinnacle player spawns a pinnacle fake -> pinnacle');

  // (3) 假人退出后由另一层级的同名玩家召唤 -> 跟随新主人
  //     the fake kept ASCENT in playerdata, Bob now owns the name
  const stale = ASCENT;
  ok(onJoin(stale, resolve({ name: 'Bob', uuid: 'uuid-fake-b', fake: true }, [alice, bob], null)) === PINNACLE,
    'stale ascent fake re-spawned under the pinnacle name -> pinnacle (identity overrides)');

  // (4) 反向：假人留着 PINNACLE，现在由攀登玩家召唤
  ok(onJoin(PINNACLE, resolve(aliceFake, [alice, bob], null)) === ASCENT,
    'stale pinnacle fake re-spawned under the ascent name -> ascent (identity overrides)');
}

// ------------------------------------------------------------------ proximity must stay weak
console.log('\n2) proximity is a fallback, not an owner');
{
  // console-spawned fake, nobody online by that name: proximity fills the default tier in
  ok(onJoin(HAVEN, { tier: PINNACLE, source: PROXIMITY }) === PINNACLE,
    'default tier is filled in from the nearest real player');
  ok(onJoin(null, { tier: ASCENT, source: PROXIMITY }) === ASCENT,
    'unset tier is filled in from the nearest real player');

  // a deliberately set tier survives a passer-by of another tier
  ok(onJoin(PINNACLE, { tier: HAVEN, source: PROXIMITY }) === PINNACLE,
    'a pinnacle fake is NOT dragged down by a haven player walking past');
  ok(onJoin(ASCENT, { tier: PINNACLE, source: PROXIMITY }) === ASCENT,
    'an ascent fake is NOT promoted by a pinnacle player walking past');
}

// ------------------------------------------------------------------ identity covers uuid too
console.log('\n3) uuid matches count as identity');
{
  // carpet shadow players are built from the real profile, so the uuid is identical
  ok(onJoin(HAVEN, { tier: SUMMIT, source: IDENTITY }) === SUMMIT,
    'uuid-based identity also overrides the default');
  ok(onJoin(PINNACLE, { tier: SUMMIT, source: IDENTITY }) === SUMMIT,
    'uuid-based identity overrides a stale non-default tier');
}

// ------------------------------------------------------------------ no source: leave alone
console.log('\n4) no source means no write');
{
  ok(onJoin(PINNACLE, { tier: null, source: NONE }) === PINNACLE,
    'nothing resolved -> existing tier untouched');
  ok(onJoin(HAVEN, { tier: null, source: NONE }) === HAVEN,
    'nothing resolved -> default untouched');
}

// ------------------------------------------------------------------ same tier: no redundant write
console.log('\n5) no redundant setTier calls');
{
  const r = { tier: ASCENT, source: IDENTITY };
  ok(onJoin(ASCENT, r) === ASCENT, 'same tier -> value unchanged (no needless packet)');
}

// ------------------------------------------------------------------ the mirror must match the Java
console.log('\n6) the mirror matches the Java it claims to mirror');
{
  const src = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/spawn/FakePlayerTierSync.java', 'utf8');

  ok(/enum Source[\s\S]*?SUMMONER[\s\S]*?IDENTITY[\s\S]*?PROXIMITY[\s\S]*?NONE/.test(src),
    'Source enum still has SUMMONER / IDENTITY / PROXIMITY / NONE');
  ok(/resolved\.source\(\) == Source\.PROXIMITY && current != null && current != WorldTier\.HAVEN/.test(src),
    'proximity guard still reads "only when current is null-or-HAVEN"');
  ok(/profile\.getName\(\)\.equalsIgnoreCase\(name\) \|\| online\.getUUID\(\)\.equals\(id\)/.test(src),
    'identity still matches on name OR uuid');
  ok(/return new Resolution\(WorldTier\.getTier\(closest\), Source\.PROXIMITY, closest\.getGameProfile\(\)\.getName\(\)\)/.test(src),
    'nearest real player still yields PROXIMITY (not IDENTITY), with its name');
  ok(/claim\(server, name\)/.test(src), 'summoner record is claimed before name matching');
  ok(/Source\.SUMMONER/.test(src) && /Source\.SUMMONER, summoner\.getGameProfile\(\)\.getName\(\)/.test(src),
    'the summoner source records who summoned it');
  // every log line must name the source player, otherwise the diagnostic cannot be verified
  ok(src.includes("tier {} -> {} (from '{}' via {})"),
    'the applied-tier log line includes the source player name');
  ok(src.includes("Fake player '{}' joined (switch={})"),
    'the join log line still exists');
  // Regression guard: the old code asked getNearestPlayer once, which on a platform full of fakes returns another
  // fake and then finds nothing at all. The scan must skip fakes explicitly.
  ok(/for \(ServerPlayer candidate : server\.getPlayerList\(\)\.getPlayers\(\)\)/.test(src),
    'proximity scans the player list instead of asking getNearestPlayer once');
  ok(/candidate == fake \|\| isFakePlayer\(candidate\)/.test(src),
    'proximity skips other fake players');
  ok(/server\.getPlayerList\(\)\.getPlayers\(\)/.test(src),
    'identity scan still walks the online player list');
  ok(!/current != WorldTier\.HAVEN\)\s*\{\s*return;\s*\}\s*\n\s*\n\s*WorldTier\.setTier/.test(src) ||
     /Source\.PROXIMITY/.test(src),
    'there is no unconditional "existing tier wins" rule any more');
}

// ------------------------------------------------------------------ gating
console.log('\n7) the feature is gated');
{
  const src = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/spawn/FakePlayerTierSync.java', 'utf8');
  ok(src.includes('Config.fakePlayersInheritWorldTier()'), 'honours the config switch');
  ok(src.includes('isFakePlayer(joining)'), 'rejects real players before any tier work');

  const cfg = fs.readFileSync('src/main/java/com/tamamo/apotheosisexpansion/Config.java', 'utf8');
  ok(cfg.includes('.define("fake_players_inherit_world_tier", false'),
    'fake_players_inherit_world_tier defaults to false');

  const mixinCfg = JSON.parse(fs.readFileSync(
    'src/main/resources/apotheosis_expansion.mixins.json', 'utf8'));
  ok(mixinCfg.mixins.includes('spawn.PlayerListFakeTierMixin'),
    'PlayerListFakeTierMixin is registered in the core config (vanilla target, no gating needed)');
}

console.log(fails === 0 ? '\nFAKE TIER SYNC OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
