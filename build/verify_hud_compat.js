// Statically verifies the HUD-compat mixins (MiniHUD / Servux) against the real mod jars.
//
// These mixins target classes in OTHER mods, which is the highest-risk kind of injection in this project:
// a wrong method name or descriptor fails at apply time (defaultRequire = 1), and a missing target mod would
// fail class resolution outright. Both hazards are checked here, without launching the game:
//
//   1. the compat mixin config is registered in neoforge.mods.toml and is not "required": true
//   2. a config plugin exists and gates each mixin on the corresponding mod id
//   3. each target class exists in the installed jar
//   4. each target method exists and the redirect anchor appears exactly once in it
//   5. the redirect handler arity matches Mixin's calling convention
//   6. the cold switch exists and defaults to false
//
// Run from the project root:  node build/verify_hud_compat.js
// Skips (with a PASS note) any target mod that is not present, exactly like the game does.
const { javap, MCJarIfPresent } = require('./lib_paths');

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };
const skip = (m) => console.log('  SKIP ' + m);

const JAVA = javap();

/** Mods directory: overridable, defaults to the launcher the user actually runs. */
const MODS_DIR = process.env.MC_MODS_DIR ||
  'F:/MC Launcher/.minecraft/versions/1.21.1/mods';

const runJavap = (...args) =>
  execFileSync(JAVA, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

function findModJar(prefix) {
  if (!fs.existsSync(MODS_DIR)) return null;
  const hit = fs.readdirSync(MODS_DIR)
    .filter(f => f.endsWith('.jar') && f.toLowerCase().startsWith(prefix))
    .sort()
    .pop();
  return hit ? path.join(MODS_DIR, hit) : null;
}

/**
 * Extracts one method body from `javap -c` output.
 * Matched loosely because synthetic lambdas carry no access modifier worth relying on.
 */
function methodBody(verbose, name) {
  const header = new RegExp('^\\s+(?:[\\w.$\\[\\]<>]+\\s+)*' + name.replace(/\$/g, '\\$') + '\\(');
  const lines = verbose.split('\n');
  const start = lines.findIndex(l => header.test(l));
  if (start < 0) return null;
  const body = [];
  for (let i = start + 1; i < lines.length; i++) {
    if (/^\s+(public|private|protected|static|final|abstract|native|synchronized)\s.*\(/.test(lines[i])) break;
    body.push(lines[i]);
  }
  return body.join('\n');
}

const REDIRECT_SIG = 'net/minecraft/world/entity/MobCategory.getMaxInstancesPerChunk:()I';

// ---------------------------------------------------------------- 1. registration
console.log('1) mixin package hygiene');
{
  // Mixin treats every package declared by a config as "owned": any class inside it is parsed as a mixin, and
  // referencing one directly throws IllegalClassLoadError at runtime. This crashed the game once - a plain
  // interface (LocalMobCapAccess) lived under ...mixin.spawn - so it is checked before anything else.
  const configs = ['apotheosis_expansion.mixins.json', 'apotheosis_expansion.compat.mixins.json'];
  const ownedRoots = configs
    .map(f => JSON.parse(fs.readFileSync(path.join('src/main/resources', f), 'utf8')).package)
    .map(p => p.replace(/\./g, '/'));

  const javaRoot = 'src/main/java';
  const offenders = [];
  (function walk(dir) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) { walk(p); continue; }
      if (!e.name.endsWith('.java')) continue;
      const rel = p.replace(/\\/g, '/');
      const pkgOfFile = rel.substring(javaRoot.length + 1).replace(/\/[^/]+$/, '');
      if (!ownedRoots.some(root => pkgOfFile === root || pkgOfFile.startsWith(root + '/'))) continue;
      const src = fs.readFileSync(p, 'utf8');
      if (!/@Mixin\s*\(/.test(src)) offenders.push(rel);
    }
  })(javaRoot);

  ok(offenders.length === 0,
    'every class under a declared mixin package is a @Mixin (' + offenders.length + ' offender(s))');
  for (const o of offenders) console.log('       NOT A MIXIN: ' + o);

  // The plugin listed by the compat config must exist at that exact path.
  const compat = JSON.parse(fs.readFileSync('src/main/resources/apotheosis_expansion.compat.mixins.json', 'utf8'));
  const pluginPath = path.join(javaRoot, compat.plugin.replace(/\./g, '/') + '.java');
  ok(fs.existsSync(pluginPath), 'config plugin exists at ' + compat.plugin);
}

console.log('\n2) compat mixin config registration');
{
  const toml = fs.readFileSync('src/main/resources/META-INF/neoforge.mods.toml', 'utf8');
  ok(toml.includes('apotheosis_expansion.compat.mixins.json'),
    'compat config listed in neoforge.mods.toml');

  const cfgPath = 'src/main/resources/apotheosis_expansion.compat.mixins.json';
  ok(fs.existsSync(cfgPath), 'compat mixin config file exists');
  const cfg = JSON.parse(fs.readFileSync(cfgPath, 'utf8'));
  ok(cfg.required === false, '"required": false so a missing target mod cannot fail the config');
  ok(typeof cfg.plugin === 'string' && cfg.plugin.endsWith('CompatMixinPlugin'),
    'a config plugin gates the mixins');
  for (const n of ['MiniHudMobCapMixin', 'ServuxMobCapMixin']) {
    ok(cfg.mixins.includes(n), n + ' listed in the compat config');
  }
  // the two configs must not share a package, or the plugin would gate the core mixins too
  const core = JSON.parse(fs.readFileSync('src/main/resources/apotheosis_expansion.mixins.json', 'utf8'));
  ok(core.package !== cfg.package, 'core and compat configs use different packages');
}

// ---------------------------------------------------------------- 2. plugin gating
console.log('\n2) plugin gates each mixin on its mod id');
{
  const src = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/compat/CompatMixinPlugin.java', 'utf8');
  ok(src.includes('IMixinConfigPlugin'), 'implements IMixinConfigPlugin');
  ok(src.includes('shouldApplyMixin'), 'overrides shouldApplyMixin');
  for (const [mixin, mod] of [['MiniHudMobCapMixin', 'minihud'], ['ServuxMobCapMixin', 'servux'],
                              ['CarpetPlayerCommandMixin', 'carpet']]) {
    ok(new RegExp(`"${mixin}",\\s*"${mod}"`).test(src),
      mixin + ' gated on mod id "' + mod + '"');
  }
  ok(src.includes('isLoaded'), 'consults ModList.isLoaded');
  // Regression guard: gating on ModList alone silently disabled every compat mixin once, because the plugin runs
  // before the mod list is reliably populated. There must be a second signal.
  ok(src.includes('getResource'), 'falls back to a class-loader resource probe when ModList cannot answer');
  // Regression guard: an earlier version used Class.forName to probe. That defines the class before Mixin can
  // transform it, so the mixin silently never applies - the exact failure this file now guards against.
  ok(!/Class\.forName\s*\(/.test(src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '')),
    'the probe does NOT use Class.forName (which would load the class before Mixin can transform it)');
  ok(/isClassPresent\(entry\.targetClass\(\)/.test(src), 'the probe uses the target class, not the mod id');
  // Fail-open: an unlisted mixin must still be applied.
  ok(/return true;\s*\}\s*\/\/ An unknown mixin|An unknown mixin should not be silently dropped[\s\S]{0,200}return true;/
      .test(src), 'unlisted mixins fail open');
}

// ---------------------------------------------------------------- 3. MiniHUD target
console.log('\n3) MiniHUD target');
const minihudJar = findModJar('minihud');
if (!minihudJar) {
  skip('MiniHUD not installed in ' + MODS_DIR + ' (mixin will not apply)');
} else {
  console.log('     jar: ' + path.basename(minihudJar));
  const TARGET = 'fi.dy.masa.minihud.data.MobCapDataHandler';
  const LAMBDA = 'lambda$updateIntegratedServerMobCaps$1';

  let verbose;
  try {
    verbose = runJavap('-p', '-c', '-s', '-classpath', minihudJar, TARGET);
  } catch (e) {
    verbose = '';
  }
  ok(verbose.length > 0, TARGET + ' present in the jar');
  if (verbose.length === 0) {
    ok(false, 'cannot disassemble the MiniHUD target');
  } else {
    const body = methodBody(verbose, LAMBDA);
    ok(body !== null, LAMBDA + ' exists (the synthetic method the redirect targets)');
    if (body !== null) {
      const hits = (body.match(/MobCategory\.getMaxInstancesPerChunk/g) || []).length;
      ok(hits === 1, 'exactly one getMaxInstancesPerChunk call inside it (found ' + hits + ')');
      ok(body.includes(REDIRECT_SIG), 'the call descriptor matches the @At target exactly');
      ok(body.includes('imul') && body.includes('idiv'),
        'vanilla multiply/divide still surrounds the call, so only the operand changes');
    }
  }

  // handler arity: receiver + 2 lambda params
  const mixinSrc = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/mixin/compat/MiniHudMobCapMixin.java', 'utf8');
  const m = mixinSrc.match(/private int [\w$]+\(([^)]*)\)/);
  const params = m ? m[1].split(',').map(s => s.trim()).filter(Boolean) : [];
  ok(params.length === 3,
    'handler takes 3 params (receiver + MinecraftServer + ResourceKey), got ' + params.length);
  ok(mixinSrc.includes('lambda$updateIntegratedServerMobCaps$1(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/resources/ResourceKey;)V'),
    'the @Redirect method descriptor matches the compiled lambda exactly');
}

// ---------------------------------------------------------------- 4. Servux target
console.log('\n4) Servux target');
const servuxJar = findModJar('servux');
if (!servuxJar) {
  skip('Servux not installed in ' + MODS_DIR + ' (mixin will not apply)');
} else {
  console.log('     jar: ' + path.basename(servuxJar));
  const TARGET = 'fi.dy.masa.servux.loggers.DataLoggerMobCaps';
  let verbose;
  try {
    verbose = runJavap('-p', '-c', '-s', '-classpath', servuxJar, TARGET);
  } catch (e) {
    verbose = '';
  }
  ok(verbose.length > 0, TARGET + ' present in the jar');
  if (verbose.length > 0) {
    const body = methodBody(verbose, 'getResult');
    ok(body !== null, 'getResult exists');
    if (body !== null) {
      const hits = (body.match(/MobCategory\.getMaxInstancesPerChunk/g) || []).length;
      ok(hits === 1, 'exactly one getMaxInstancesPerChunk call inside getResult (found ' + hits + ')');
      ok(body.includes(REDIRECT_SIG), 'the call descriptor matches the @At target exactly');
      ok(body.includes('imul') && body.includes('idiv'),
        'vanilla multiply/divide still surrounds the call');
    }
  }
  const mixinSrc = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/mixin/compat/ServuxMobCapMixin.java', 'utf8');
  const m = mixinSrc.match(/private int [\w$]+\(([^)]*)\)/);
  const params = m ? m[1].split(',').map(s => s.trim()).filter(Boolean) : [];
  ok(params.length === 2,
    'handler takes 2 params (receiver + MinecraftServer), got ' + params.length);
}

// ---------------------------------------------------------------- 5. cold switch
console.log('\n5) cold switch');
{
  const cfg = fs.readFileSync('src/main/java/com/tamamo/apotheosisexpansion/Config.java', 'utf8');
  ok(cfg.includes('.define("sync_mob_cap_to_hud", false'),
    'sync_mob_cap_to_hud defined and defaults to false (cold switch)');
  ok(cfg.includes('public static boolean syncMobCapToHud()'), 'a getter exists');

  const bridge = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/spawn/MobCapHudBridge.java', 'utf8');
  ok(bridge.includes('Config.syncMobCapToHud()'), 'the bridge honours the switch');
  ok(bridge.includes('Config.enableMobCapScaling()'), 'the bridge also requires cap scaling to be on');
  ok(bridge.includes('category.isFriendly()'), 'friendly categories keep vanilla numbers');
  // every entry point must be able to answer "use vanilla"
  for (const fn of ['multiplierForLevel', 'multiplierForServer', 'scaledCap']) {
    ok(bridge.includes(fn + '(@Nullable') || bridge.includes(fn + '('), fn + ' exists');
  }
  ok(bridge.includes('return null;'), 'entry points return null to mean "keep vanilla behaviour"');

  for (const mixin of ['MiniHudMobCapMixin', 'ServuxMobCapMixin']) {
    const s = fs.readFileSync(
      'src/main/java/com/tamamo/apotheosisexpansion/mixin/compat/' + mixin + '.java', 'utf8');
    ok(s.includes('receiver.getMaxInstancesPerChunk()'),
      mixin + ' falls back to the receiver value, so "off" is byte-for-byte vanilla');
  }
}

// ---------------------------------------------------------------- 6. lang keys
console.log('\n6) language keys');
for (const lang of ['en_us', 'zh_cn']) {
  const json = JSON.parse(fs.readFileSync(
    'src/main/resources/assets/apotheosis_expansion/lang/' + lang + '.json', 'utf8'));
  const k = 'apotheosis_expansion.configuration.player.sync_mob_cap_to_hud';
  ok(Object.prototype.hasOwnProperty.call(json, k), lang + ' has the switch label');
  ok(Object.prototype.hasOwnProperty.call(json, k + '.tooltip'), lang + ' has its tooltip');
}


// ---------------------------------------------------------------- 9. carpet summoner capture
console.log('\n9) carpet /player spawn capture');
{
  const carpetJar = findModJar('neoforge-carpet') || findModJar('carpet');
  if (!carpetJar) {
    skip('carpet not installed in ' + MODS_DIR + ' (mixin will not apply)');
  } else {
    console.log('     jar: ' + path.basename(carpetJar));
    const TARGET = 'carpet.commands.PlayerCommand';
    let verbose = '';
    try { verbose = runJavap('-p', '-s', '-classpath', carpetJar, TARGET); } catch (e) { verbose = ''; }
    ok(verbose.length > 0, TARGET + ' present in the jar');
    if (verbose.length > 0) {
      const lines = verbose.split('\n');
      const idx = lines.findIndex(l => /private static int spawn\(com\.mojang\.brigadier\.context\.CommandContext/.test(l));
      ok(idx >= 0, 'private static int spawn(CommandContext) exists');
      ok(idx >= 0 && (lines[idx + 1] || '').includes('descriptor: (Lcom/mojang/brigadier/context/CommandContext;)I'),
        'spawn descriptor is (CommandContext;)I');

      // createFake must be invoked from *inside* spawn, which needs the disassembly (-c), not just signatures.
      const code = runJavap('-p', '-c', '-classpath', carpetJar, TARGET);
      const body = methodBody(code, 'spawn') || '';
      ok(body.includes('EntityPlayerMPFake.createFake'),
        'spawn() really calls EntityPlayerMPFake.createFake (the mixin injects the right method)');
    }
    const mixinSrc = fs.readFileSync(
      'src/main/java/com/tamamo/apotheosisexpansion/mixin/compat/CarpetPlayerCommandMixin.java', 'utf8');
    ok(mixinSrc.includes('ctx.getArgument("player"'), 'mixin reads the "player" argument');
    ok(mixinSrc.includes('source.getPlayer()'), 'mixin forwards the command source player');
    ok(mixinSrc.includes('source.getEntity()'), 'mixin forwards the source entity (covers command blocks)');
  }
  const sync = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/spawn/FakePlayerTierSync.java', 'utf8');
  ok(sync.includes('FakePlayerSummonTracker.claim'), 'tier sync claims the recorded summoner first');

  const plugin = fs.readFileSync(
    'src/main/java/com/tamamo/apotheosisexpansion/compat/CompatMixinPlugin.java', 'utf8');
  ok(/"CarpetPlayerCommandMixin",\s*"carpet"/.test(plugin), 'carpet mixin gated on mod id "carpet"');
}

console.log(fails === 0 ? '\nHUD COMPAT VERIFICATION OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
