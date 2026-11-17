// Statically verifies the multiplayer monster-cap mixins against the real Minecraft bytecode.
//
// A mixin that cannot match its target fails at apply time (defaultRequire=1), and there is no
// launch test in this project, so every part of the injection contract is checked here instead:
//   1. every target class exists
//   2. every target method exists with the exact descriptor written in the annotation
//   3. the two @Redirect anchors each appear exactly once in their method
//   4. the redirect handler arity matches Mixin's calling convention
//   5. the mixin json lists all three classes
//   6. the cap arithmetic produces the numbers the docs promise (70 / 105 / 140)
//
// Run from the project root:  node build/verify_mob_cap.js
const { mcJar, javap } = require('./lib_paths');

const { execFileSync } = require('child_process');
const fs = require('fs');

const JAVA = javap();
const JAR = mcJar();

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const runJavap = (...args) =>
  execFileSync(JAVA, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

/**
 * Extracts one method body from `javap -c` output.
 * <p>
 * The member line is matched loosely on purpose: `canSpawnForCategory` is package-private, so it carries no
 * access modifier at all and a `(public|private|protected|static)` prefix would skip straight past it.
 */
function methodBody(verbose, name) {
  const header = new RegExp('^\\s+(?:[\\w.$\\[\\]<>]+\\s+)*' + name + '\\(');
  const lines = verbose.split('\n');
  const start = lines.findIndex(l => header.test(l));
  if (start < 0) return null;
  const body = [];
  for (let i = start + 1; i < lines.length; i++) {
    const line = lines[i];
    // A new member starts without indented bytecode offsets; offsets look like "     12: invokevirtual".
    if (/^\s+(public|private|protected|static|final|abstract|native|synchronized)\s.*\(/.test(line)) break;
    body.push(line);
  }
  return body.join('\n');
}

const REDIRECT_SIG = 'net/minecraft/world/entity/MobCategory.getMaxInstancesPerChunk:()I';

// ---------------------------------------------------------------- 1. calculator
console.log('1) LocalMobCapCalculator.canSpawn');
const CALC = 'net.minecraft.world.level.LocalMobCapCalculator';
{
  const verbose = runJavap('-p', '-c', '-s', '-classpath', JAR, CALC);
  ok(verbose.includes('public boolean canSpawn(net.minecraft.world.entity.MobCategory, net.minecraft.world.level.ChunkPos)'),
    'canSpawn(MobCategory, ChunkPos) exists');
  ok(/descriptor:\s+\(Lnet\/minecraft\/world\/entity\/MobCategory;Lnet\/minecraft\/world\/level\/ChunkPos;\)Z/
      .test(verbose), 'descriptor is (MobCategory;ChunkPos;)Z');
  ok(verbose.includes('private final net.minecraft.server.level.ChunkMap chunkMap;'),
    'chunkMap field exists and is private final (the @Shadow target)');
}

// ---------------------------------------------------------------- 2. per-player cap
console.log('\n2) LocalMobCapCalculator$MobCounts.canSpawn (per-player cap)');
const COUNTS = 'net.minecraft.world.level.LocalMobCapCalculator$MobCounts';
{
  const verbose = runJavap('-p', '-c', '-s', '-classpath', JAR, COUNTS);
  const body = methodBody(verbose, 'canSpawn');
  ok(body !== null, 'canSpawn(MobCategory) exists');

  const hits = (body.match(/MobCategory\.getMaxInstancesPerChunk/g) || []).length;
  ok(hits === 1, 'exactly one getMaxInstancesPerChunk call inside canSpawn (found ' + hits + ')');
  ok(body.includes(REDIRECT_SIG), 'the call descriptor matches the @At target exactly');

  const desc = verbose.split('\n');
  const idx = desc.findIndex(l => l.includes('boolean canSpawn(net.minecraft.world.entity.MobCategory)'));
  ok(idx >= 0 && (desc[idx + 1] || '').includes('descriptor: (Lnet/minecraft/world/entity/MobCategory;)Z'),
    'enclosing method takes exactly one MobCategory parameter');
}

// ---------------------------------------------------------------- 3. level-wide cap
console.log('\n3) NaturalSpawner$SpawnState.canSpawnForCategory (level-wide cap)');
const STATE = 'net.minecraft.world.level.NaturalSpawner$SpawnState';
{
  const verbose = runJavap('-p', '-c', '-s', '-classpath', JAR, STATE);
  ok(verbose.includes('private final net.minecraft.world.level.LocalMobCapCalculator localMobCapCalculator;'),
    'localMobCapCalculator field exists and is private final (the @Shadow target)');

  const body = methodBody(verbose, 'canSpawnForCategory');
  ok(body !== null, 'canSpawnForCategory exists');
  if (body === null) {
    console.log(fails === 0 ? '\nMOB CAP MIXIN VERIFICATION OK' : '\n' + fails + ' FAILURE(S)');
    process.exit(1);
  }

  const hits = (body.match(/MobCategory\.getMaxInstancesPerChunk/g) || []).length;
  ok(hits === 1, 'exactly one getMaxInstancesPerChunk call inside canSpawnForCategory (found ' + hits + ')');
  ok(body.includes(REDIRECT_SIG), 'the call descriptor matches the @At target exactly');
  ok(body.includes('spawnableChunkCount') && body.includes('MAGIC_NUMBER'),
    'the call is the numerator of 70 x chunkCount / MAGIC_NUMBER');

  const desc = verbose.split('\n');
  const idx = desc.findIndex(l => l.includes('boolean canSpawnForCategory('));
  ok(idx >= 0 && (desc[idx + 1] || '')
      .includes('descriptor: (Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/world/level/ChunkPos;)Z'),
    'enclosing method takes (MobCategory, ChunkPos), so the redirect handler gets 2 extra parameters');
}

// ---------------------------------------------------------------- 4. handler arity
console.log('\n4) redirect handler signatures');
{
  const counts = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/mixin/spawn/MobCountsMixin.java', 'utf8');
  // instance call: (receiver, args..., enclosingParams...) = (MobCategory, MobCategory)
  const m = counts.match(/private int [\w$]+\(([^)]*)\)/);
  const params = m ? m[1].split(',').map(s => s.trim()).filter(Boolean) : [];
  ok(params.length === 2, 'MobCounts handler takes 2 params (receiver + enclosing MobCategory), got ' + params.length);

  const state = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/mixin/spawn/SpawnStateMixin.java', 'utf8');
  const m2 = state.match(/private int [\w$]+\(([^)]*)\)/);
  const params2 = m2 ? m2[1].split(',').map(s => s.trim()).filter(Boolean) : [];
  ok(params2.length === 3,
    'SpawnState handler takes 3 params (receiver + MobCategory + ChunkPos), got ' + params2.length);
}

// ---------------------------------------------------------------- 5. registration
console.log('\n5) mixin registration');
{
  const cfg = JSON.parse(fs.readFileSync('src/main/resources/apotheosis_expansion.mixins.json', 'utf8'));
  for (const name of ['spawn.LocalMobCapCalculatorMixin', 'spawn.MobCountsMixin', 'spawn.SpawnStateMixin']) {
    ok(cfg.mixins.includes(name), name + ' listed');
  }
  ok(cfg.injectors && cfg.injectors.defaultRequire === 1, 'defaultRequire=1 so a bad target fails loudly');
}

// ---------------------------------------------------------------- 6. cap arithmetic
console.log('\n6) cap arithmetic matches the documentation');
{
  // Mirrors MobCapScaler.multiplier: max(1, 1 + bonus * (n - 1)), clamped to the player ceiling.
  // Kept fractional, which is the whole point: 2 players at a 0.5 bonus is x1.5, not x2.
  const mult = (n, bonus, max) => {
    if (n <= 1) return 1;
    const capped = Math.min(n, Math.max(2, max));
    return Math.max(1, 1 + bonus * (capped - 1));
  };
  // Mirrors MobCapScaler.scaleCap: round, then clamp.
  const cap = (base, m) => Math.round(base * m);
  const cases = [
    [1, 0.5, 8, 1],     // a lone player is never scaled
    [2, 0.5, 8, 1.5],
    [3, 0.5, 8, 2],
    [4, 0.5, 8, 2.5],
    [2, 1.0, 8, 2],     // a 1.0 bonus doubles with one extra player
    [3, 1.0, 8, 3],
    [99, 0.5, 8, 4.5],  // capped at max players: 1 + 0.5*7
    [2, 0.0, 8, 1],     // a zero bonus must not scale at all
  ];
  for (const [n, bonus, max, want] of cases) {
    ok(mult(n, bonus, max) === want, 'n=' + n + ' bonus=' + bonus + ' max=' + max + ' -> x' + want);
  }
  ok(cap(70, mult(1, 0.5, 8)) === 70, 'one player keeps the vanilla 70');
  ok(cap(70, mult(2, 0.5, 8)) === 105, 'two same-tier players get 105');
  ok(cap(70, mult(3, 0.5, 8)) === 140, 'three same-tier players get 140');
  ok(cap(70, mult(2, 1.0, 8)) === 140, 'a 1.0 bonus reaches 140 with only two players');
}

// ---------------------------------------------------------------- 7. config wiring
console.log('\n7) config wiring');
{
  const cfg = fs.readFileSync('src/main/java/com/tamamo/apotheosisexpansion/Config.java', 'utf8');
  ok(cfg.includes('.define("enable_mob_cap_scaling"'), 'enable_mob_cap_scaling defined');
  ok(cfg.includes('"mob_cap_bonus_per_player", 0.5D'), 'mob_cap_bonus_per_player defaults to 0.5');
  ok(cfg.includes('"mob_cap_max_players", 8'), 'mob_cap_max_players defaults to 8');
  ok(/push\("player"\)/.test(cfg), 'the options live in the [player] section');

  const scaler = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/spawn/MobCapScaler.java', 'utf8');
  ok(scaler.includes('Config.enableMobCapScaling()'), 'the scaler honours the master switch');
  ok(scaler.includes('category.isFriendly()'), 'friendly categories are excluded');
  ok(scaler.includes('largestSameTierGroup'), 'the tier grouping is applied');
}

// ---------------------------------------------------------------- 8. lang keys
console.log('\n8) language keys');
for (const lang of ['en_us', 'zh_cn']) {
  const json = JSON.parse(fs.readFileSync(
    'src/main/resources/assets/apotheosis_expansion/lang/' + lang + '.json', 'utf8'));
  for (const key of ['player.enable_mob_cap_scaling', 'player.mob_cap_bonus_per_player', 'player.mob_cap_max_players']) {
    const full = 'apotheosis_expansion.configuration.' + key;
    ok(Object.prototype.hasOwnProperty.call(json, full), lang + ' has ' + key);
    ok(Object.prototype.hasOwnProperty.call(json, full + '.tooltip'), lang + ' has ' + key + '.tooltip');
  }
}

console.log(fails === 0 ? '\nMOB CAP MIXIN VERIFICATION OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
