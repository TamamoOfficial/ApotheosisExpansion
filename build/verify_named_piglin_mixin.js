// Statically verifies NamedPiglinConversionMixin against the real compiled Minecraft/NeoForge artifact.
//
// Same reasoning as verify_nametag_mixin.js: a mixin mismatch either crashes at apply time
// (defaultRequire = 1) or silently never fires. Everything the mixin assumes is checked against bytecode:
//   1. AbstractPiglin exists and declares isConverting returning boolean (callback type + cancellable)
//   2. the name is the Mojang production name, so the no-refmap `remap = false` anchor can match at runtime
//   3. isConverting really is THE zombification gate: customServerAiStep calls it before counting
//      timeInOverworld, and the conversion fires past CONVERSION_TIME inside the same guard
//   4. EventHooks.canLivingConvert is the NeoForge escape hatch on the same branch (documents why the
//      event was not used: it fires every tick from the 300th on and cannot stop the timer cheaply)
//   5. Piglin and PiglinBrute both extend AbstractPiglin (the mixin covers both), while Hoglin keeps
//      its own private isConverting and must NOT be a mixin target (feature scope: piglins only)
//   6. the mixin source: remap = false, CallbackInfoReturnable<Boolean>, no setReturnValue drift on
//      other values, tag check against NamedMobPeacefulProtection.PROTECTED_TAG
//   7. the config key exists and defaults to true, and the adventure section registers it
const { rel, javap, mcJar, check } = require('./lib_paths');
const { execFileSync } = require('child_process');
const fs = require('fs');

const JAVA = javap();
const JAR = mcJar();
const MIXIN_SRC = rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'mixin', 'NamedPiglinConversionMixin.java');
const CONFIG_SRC = rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'Config.java');

const runJavap = (...args) => execFileSync(JAVA, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

let fails = 0;
const ok = (state, message) => { if (!check(state, message)) fails++; };

// ---- 1/2. target class + production method name
console.log('1) AbstractPiglin.isConverting');
const abs = runJavap('-p', '-s', '-classpath', JAR, 'net.minecraft.world.entity.monster.piglin.AbstractPiglin');
ok(abs.includes('class net.minecraft.world.entity.monster.piglin.AbstractPiglin extends'), 'AbstractPiglin resolved');
ok(/public boolean isConverting\(\)/.test(abs), 'isConverting()Z declared with its production name (no-refmap anchor works)');
ok(/protected void finishConversion\(net.minecraft.server.level.ServerLevel\)/.test(abs), 'finishConversion(ServerLevel)V declared');
ok(abs.includes('timeInOverworld'), 'timeInOverworld field exists (the 300-tick counter)');

// ---- 3. isConverting really gates the conversion in customServerAiStep
// javap prints same-class calls without an owner ("Method isConverting:()Z") and cross-class calls
// with SLASHED binary names ("Method net/neoforged/..."), so the patterns below use those forms.
console.log('3) customServerAiStep calls the gate');
const body = runJavap('-p', '-c', '-classpath', JAR, 'net.minecraft.world.entity.monster.piglin.AbstractPiglin');
const aiStart = body.indexOf('protected void customServerAiStep();');
const ai = body.slice(aiStart, body.indexOf('lambda$customServerAiStep', aiStart));
ok(/invokevirtual .*Method isConverting:\(\)Z/.test(ai), 'customServerAiStep calls isConverting');
ok(/Field timeInOverworld:I/.test(ai), 'customServerAiStep touches timeInOverworld');
ok(/sipush\s+300/.test(ai), 'the threshold is 300 ticks');
ok(/EventHooks\.canLivingConvert:/.test(ai), 'the conversion branch posts LivingConversionEvent.Pre');
ok(ai.includes('Field net/minecraft/world/entity/EntityType.ZOMBIFIED_PIGLIN'), 'the branch converts to ZOMBIFIED_PIGLIN');
ok(/invokevirtual .*finishConversion:/.test(ai), 'and calls finishConversion on pass');

// ---- 5. class hierarchy / scope
console.log('5) scope: Piglin + PiglinBrute covered, Hoglin untouched');
const piglin = runJavap('-p', '-s', '-classpath', JAR, 'net.minecraft.world.entity.monster.piglin.Piglin');
const brute = runJavap('-p', '-s', '-classpath', JAR, 'net.minecraft.world.entity.monster.piglin.PiglinBrute');
const hoglin = runJavap('-p', '-s', '-classpath', JAR, 'net.minecraft.world.entity.monster.hoglin.Hoglin');
ok(/class net\.minecraft\.world\.entity\.monster\.piglin\.Piglin extends net\.minecraft\.world\.entity\.monster\.piglin\.AbstractPiglin/.test(piglin), 'Piglin extends AbstractPiglin');
ok(/class net\.minecraft\.world\.entity\.monster\.piglin\.PiglinBrute extends net\.minecraft\.world\.entity\.monster\.piglin\.AbstractPiglin/.test(brute), 'PiglinBrute extends AbstractPiglin');
ok(/boolean isConverting\(\)/.test(hoglin), 'Hoglin declares its OWN isConverting (not inherited, so this mixin cannot reach it, by design)');
ok(!/class net\.minecraft\.world\.entity\.monster\.hoglin\.Hoglin extends net\.minecraft\.world\.entity\.monster\.piglin\.AbstractPiglin/.test(hoglin), 'Hoglin does NOT extend AbstractPiglin');

// ---- 6. mixin source
console.log('6) mixin source');
const raw = fs.readFileSync(MIXIN_SRC, 'utf8');
const src = raw.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
ok(src.includes('net.minecraft.world.entity.monster.piglin.AbstractPiglin'), 'mixin imports AbstractPiglin');
ok(/method = "isConverting"/.test(src), 'mixin targets isConverting');
ok(/remap = false/.test(src), 'mixin pins remap = false (no refmap in this project)');
ok(/CallbackInfoReturnable<Boolean>/.test(src), 'callback type matches the boolean target');
ok(/cancellable = true/.test(src), 'injection is cancellable (setReturnValue needs it)');
ok(/cir\.setReturnValue\(false\)/.test(src), 'forces the gate to false on the tagged mob');
ok(src.includes('Config.enableNamedPiglinNoZombify()'), 'handler respects its own config toggle');
ok(src.includes('NamedMobPeacefulProtection.PROTECTED_TAG'), 'gate keys off the player-name tag');

// ---- 7. config wiring
console.log('7) config wiring');
const cfg = fs.readFileSync(CONFIG_SRC, 'utf8');
ok(/ENABLE_NAMED_PIGLIN_NO_ZOMBIFY/.test(cfg), 'Config declares ENABLE_NAMED_PIGLIN_NO_ZOMBIFY');
ok(cfg.includes('.define("enable_named_piglin_no_zombify", true)'), 'key defined in [adventure], default true');
ok(cfg.includes('.translation(KEY + "adventure.enable_named_piglin_no_zombify")'), 'explicit translation key set');
ok(/public static boolean enableNamedPiglinNoZombify\(\)/.test(cfg), 'typed getter exists');
const mixinJson = JSON.parse(fs.readFileSync(rel('src', 'main', 'resources', 'apotheosis_expansion.mixins.json'), 'utf8'));
ok(mixinJson.mixins.includes('NamedPiglinConversionMixin'), 'mixin registered in apotheosis_expansion.mixins.json');

// ---- lang
console.log('8) lang keys');
for (const [f, expect] of [
  ['en_us.json', 'Named piglins never zombify'],
  ['zh_cn.json', '命名猪灵不变僵尸猪灵'],
]) {
  const lang = JSON.parse(fs.readFileSync(rel('src', 'main', 'resources', 'assets', 'apotheosis_expansion', 'lang', f), 'utf8'));
  const k = 'apotheosis_expansion.configuration.adventure.enable_named_piglin_no_zombify';
  ok(lang[k] === expect, f + ' titles the toggle');
  ok(typeof lang[k + '.tooltip'] === 'string' && lang[k + '.tooltip'].length > 40, f + ' has a tooltip');
}

console.log('');
if (fails) {
  console.log('FAILED: ' + fails + ' check(s)');
  process.exit(1);
}
console.log('All checks passed.');
