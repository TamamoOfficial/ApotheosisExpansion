// Statically verifies the CreativeTargetFilter mixins against the real Apotheosis jar.
//
// A redirect that cannot match fails at class-transform time (defaultRequire=1), and the user is
// not launch-testing, so each part of the contract is checked here instead.
const { apothJar, mcSourcesJar, javap } = require('./lib_paths');

const { execFileSync } = require('child_process');
const fs = require('fs');

const JAVA = javap();
const JAR = apothJar();
const CP = fs.readFileSync('cp.txt', 'utf8').trim(); // full compile classpath (has Minecraft classes)

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const runJavap = (...a) => execFileSync(JAVA, a, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
const splitLines = s => s.split(String.fromCharCode(10)).map(l => l.trim());

const SET_TARGET_DESC = '(Lnet/minecraft/world/entity/LivingEntity;)V';

/** Extracts the body of one method (by descriptor substring) from javap -c -s output. */
function methodBody(dump, descriptorNeedle) {
  const all = splitLines(dump);
  const startIdx = all.findIndex(l => l === 'descriptor: ' + descriptorNeedle);
  if (startIdx < 0) return null;

  // walk backwards to the signature line, forwards to the next signature
  let end = all.length;
  for (let i = startIdx + 1; i < all.length; i++) {
    if (/(private|public|protected|static).*\(.*\);$/.test(all[i])) { end = i; break; }
  }
  return all.slice(startIdx, end).join(String.fromCharCode(10));
}

const TARGETS = [
  {
    label: 'natural spawn',
    cls: 'dev.shadowsoffire.apotheosis.mobs.ApothMobEvents',
    desc: '(Lnet/neoforged/neoforge/event/entity/living/FinalizeSpawnEvent;Lnet/minecraft/world/entity/Mob;Ldev/shadowsoffire/apotheosis/tiers/GenContext;Lnet/minecraft/world/entity/player/Player;)Z',
    name: 'trySpawnInvader',
  },
  {
    label: 'boss spawner',
    cls: 'dev.shadowsoffire.apotheosis.mobs.BossSpawnerBlock$BossSpawnerTile',
    desc: '(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V',
    name: 'lambda$serverTick$0',
  },
];

for (const t of TARGETS) {
  console.log('\n== ' + t.label + ' -> ' + t.cls + '#' + t.name);

  const listing = execFileSync('unzip', ['-l', JAR], { encoding: 'utf8' });
  ok(listing.includes(t.cls.replace(/\./g, '/') + '.class'), 'target class present in jar');

  const dump = runJavap('-p', '-c', '-s', '-classpath', JAR, t.cls);
  const body = methodBody(dump, t.desc);
  ok(body !== null, 'method ' + t.name + ' present with descriptor ' + t.desc);
  if (body === null) continue;

  const count = (body.match(/Mob\.setTarget/g) || []).length;
  ok(count === 1, 'exactly one Mob.setTarget INVOKE in that method (found ' + count + ')');
  ok(body.includes('Mob.setTarget:(Lnet/minecraft/world/entity/LivingEntity;)V'),
    'invocation descriptor matches the redirect target ' + SET_TARGET_DESC);
}

// ---------------------------------------------------------------- Mob.setTarget itself
console.log('\n== Mob.setTarget descriptor (from the Minecraft classpath)');
const mobDump = runJavap('-p', '-s', '-classpath', CP, 'net.minecraft.world.entity.Mob');
const mobLines = splitLines(mobDump);
const idx = mobLines.findIndex(l => l.includes('public void setTarget(net.minecraft.world.entity.LivingEntity)'));
ok(idx >= 0, 'Mob.setTarget(LivingEntity) exists');
ok(idx >= 0 && (mobLines[idx + 1] || '') === 'descriptor: ' + SET_TARGET_DESC,
  'descriptor is ' + SET_TARGET_DESC);

// ---------------------------------------------------------------- EntitySelector predicate
console.log('\n== EntitySelector.NO_CREATIVE_OR_SPECTATOR semantics');
const src = mcSourcesJar();
const sel = execFileSync('unzip', ['-p', src, 'net/minecraft/world/entity/EntitySelector.java'], { encoding: 'utf8' });
ok(sel.includes('NO_CREATIVE_OR_SPECTATOR = p_20436_ -> !(p_20436_ instanceof Player)') &&
   sel.includes('!p_20436_.isSpectator() && !((Player)p_20436_).isCreative()'),
  'predicate excludes both spectator and creative');

// ---------------------------------------------------------------- mixin registration
console.log('\n== mixin registration');
const cfg = JSON.parse(fs.readFileSync('src/main/resources/apotheosis_expansion.mixins.json', 'utf8'));
ok(cfg.mixins.includes('CreativeTargetFilter$NaturalSpawn'), 'CreativeTargetFilter$NaturalSpawn registered');
ok(cfg.mixins.includes('CreativeTargetFilter$BossSpawner'), 'CreativeTargetFilter$BossSpawner registered');
ok(cfg.injectors && cfg.injectors.defaultRequire === 1, 'defaultRequire=1 so a bad target fails loudly');
ok(!('refmap' in cfg), 'no refmap declared, consistent with the remap=false choice');

// ---------------------------------------------------------------- bytecode annotation strings
console.log('\n== bytecode annotation strings');
for (const inner of ['NaturalSpawn', 'BossSpawner']) {
  const verb = runJavap('-v', '-p', '-classpath', 'build/classes',
    'com.tamamo.apotheosisexpansion.mixin.CreativeTargetFilter$' + inner);
  ok(verb.includes('Lnet/minecraft/world/entity/Mob;setTarget(Lnet/minecraft/world/entity/LivingEntity;)V'),
    inner + ': redirect target string present in the class file');
  ok(/remap=false/.test(verb.replace(/\s+/g, '')), inner + ': remap=false present in the annotation');
}

console.log(fails === 0 ? '\nCREATIVE TARGET FILTER VERIFICATION OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
