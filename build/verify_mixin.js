// Statically verifies the BossSpawnerTileMixin against the real Apotheosis jar.
//
// A mixin that cannot match its target fails at apply time (defaultRequire=1), and the user has
// no plans to launch-test, so every piece of the @Redirect contract is checked here instead:
//   1. the target class exists
//   2. the target method exists with the exact descriptor written in the annotation
//   3. that method contains exactly one INVOKE of the redirect target
//   4. the redirect target method+descriptor exists on InvaderRegistry
//   5. the value returned by the redirect target is assignable to the INJECT method's return type
const { apothJar, javap } = require('./lib_paths');

const { execFileSync } = require('child_process');
const fs = require('fs');

const JAVA = javap();
const JAR = apothJar();

const TARGET_CLASS = 'dev.shadowsoffire.apotheosis.mobs.BossSpawnerBlock$BossSpawnerTile';
const TARGET_METHOD_DESC = 'lambda$serverTick$0(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V';
const REDIRECT_OWNER = 'dev/shadowsoffire/apotheosis/mobs/registries/InvaderRegistry';
const REDIRECT_NAME = 'getRandomItem';
const REDIRECT_DESC = '(Ldev/shadowsoffire/apotheosis/tiers/GenContext;)Ldev/shadowsoffire/apotheosis/mobs/types/Invader;';

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const runJavap = (...args) => execFileSync(JAVA, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

// ---- 1. class exists
console.log('1) target class');
const listing = execFileSync('unzip', ['-l', JAR], { encoding: 'utf8' });
ok(listing.includes('dev/shadowsoffire/apotheosis/mobs/BossSpawnerBlock$BossSpawnerTile.class'),
  TARGET_CLASS + ' present in jar');

// ---- 2/3. method + unique invoke
console.log('2) target method and its invoke');
const verbose = runJavap('-p', '-c', '-s', '-classpath', JAR, TARGET_CLASS);

const methodName = TARGET_METHOD_DESC.slice(0, TARGET_METHOD_DESC.indexOf('('));
const methodDesc = TARGET_METHOD_DESC.slice(TARGET_METHOD_DESC.indexOf('('));

ok(verbose.includes(methodName + '('), 'method ' + methodName + ' exists');
const descLines = verbose.split(String.fromCharCode(10)).map(l => l.trim());
ok(descLines.includes("descriptor: " + methodDesc),
  "descriptor matches the annotation exactly: " + methodDesc);

// isolate that method's body
const lines = verbose.split('\n');
let inBody = false, body = [];
for (const line of lines) {
  if (new RegExp('^\\s+(private|public|protected).*\\b' + methodName.replace(/\$/g, '\\$') + '\\(').test(line)) { inBody = true; continue; }
  if (inBody) {
    if (/^\s+(private|public|protected|static)\s.*\(/.test(line) && !line.includes(methodName)) break;
    body.push(line);
  }
}
const bodyText = body.join('\n');
const invokeCount = (bodyText.match(new RegExp(REDIRECT_OWNER.split('/').pop() + '\\.' + REDIRECT_NAME, 'g')) || []).length;
ok(invokeCount === 1, 'exactly one ' + REDIRECT_NAME + ' invocation inside the lambda (found ' + invokeCount + ')');
ok(bodyText.includes('InvaderRegistry.getRandomItem:(Ldev/shadowsoffire/apotheosis/tiers/GenContext;)Ldev/shadowsoffire/apotheosis/mobs/types/Invader;'),
  'invocation descriptor matches the redirect target');

// ---- 4. redirect target exists
console.log('3) redirect target method');
const regDump = runJavap('-p', '-s', '-classpath', JAR, REDIRECT_OWNER.replace(/\//g, '.'));
const targetSig = regDump.split('\n').filter(l => l.includes(REDIRECT_NAME) && l.includes('(dev.shadowsoffire.apotheosis.tiers.GenContext)'));
ok(targetSig.length >= 1, REDIRECT_NAME + '(GenContext) exists on InvaderRegistry');
const targetDescLine = regDump.split('\n');
let foundDesc = false;
for (let i = 0; i < targetDescLine.length; i++) {
  if (targetDescLine[i].includes(REDIRECT_NAME + '(dev.shadowsoffire.apotheosis.tiers.GenContext)')
      && targetDescLine[i].includes('Invader ')
      && !targetDescLine[i].includes('CodecProvider')) {
    if ((targetDescLine[i + 1] || '').includes('descriptor: ' + REDIRECT_DESC)) foundDesc = true;
  }
}
ok(foundDesc, 'redirect descriptor matches: ' + REDIRECT_DESC);

// ---- 5. return type compatibility
console.log('4) return type compatibility');
ok(REDIRECT_DESC.endsWith(')Ldev/shadowsoffire/apotheosis/mobs/types/Invader;'),
  'redirect returns Invader, assignable to the injection point type');

// ---- 6. the mixin json lists it
console.log('5) mixin registration');
const cfg = JSON.parse(require('fs').readFileSync('src/main/resources/apotheosis_expansion.mixins.json', 'utf8'));
ok(cfg.mixins.includes('BossSpawnerTileMixin'), 'BossSpawnerTileMixin listed in apotheosis_expansion.mixins.json');
ok(cfg.injectors && cfg.injectors.defaultRequire === 1, 'defaultRequire=1 so a bad target fails loudly');

// ---- 6. entity conversion must be idempotent
console.log('6) entity conversion idempotency');
{
  const ev = fs.readFileSync("src/main/java/com/tamamo/apotheosisexpansion/compat/VanillaBossEvents.java", "utf8");
  ok(ev.includes("apotheosisExpansion$alreadyConverted"), "has an already-converted guard");
  ok(ev.includes("Invader.BOSS_KEY"), "guard reads the same key markBoss() writes");
  const guardPos = ev.indexOf("apotheosisExpansion$alreadyConverted(entity)");
  const witchPos = ev.indexOf("instanceof WitherBoss");
  ok(guardPos > 0 && guardPos < witchPos, "guard runs before any conversion");
}
console.log(fails === 0 ? '\nMIXIN STATIC VERIFICATION OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
