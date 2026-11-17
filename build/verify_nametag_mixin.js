// Statically verifies NameTagItemMixin against the real Minecraft/NeoForge artifact.
//
// Mixin cannot be exercised without launching the game, and a mismatch only shows up as a crash
// at apply time (this project uses defaultRequire = 1) or - worse - as a silently never-firing
// injection. Both failure modes have already happened once on this feature, so everything the
// mixin assumes is checked against the compiled class:
//   1. net.minecraft.world.item.NameTagItem exists
//   2. it declares interactLivingEntity with the descriptor the @Inject matches
//   3. the method has MORE THAN ONE return, which is why @At("TAIL") must not be used here
//   4. the injection anchor (INVOKE ItemStack.shrink) occurs EXACTLY ONCE, so the injection is
//      unambiguous
//   5. that anchor is on the success branch: after setPersistenceRequired (mob was named) and
//      after setCustomName, and before the sidedSuccess return
//   6. the mixin source declares a plain CallbackInfo with the anchor and no TAIL/RETURN drift
//   7. Entity.addTag / getTags exist, so the tag marker is the vanilla scoreboard tag
const { rel, javap, check } = require('./lib_paths');
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const JAVA = javap();

/**
 * The compiled Minecraft jar, not the sources jar: javap needs bytecode.
 * moddev writes the merged artifact during the first build; there is no env-var override because
 * the path is deterministic once `gradle build` has run.
 */
function mcClassesJar() {
  const dir = rel('build', 'moddev', 'artifacts');
  if (!fs.existsSync(dir)) {
    console.error('MISSING ' + dir + ' - run "gradlew build" once first.');
    process.exit(2);
  }
  const hit = fs.readdirSync(dir).find(f => /^neoforge-.*-merged\.jar$/.test(f));
  if (!hit) {
    console.error('MISSING neoforge-*-merged.jar in ' + dir + ' - run "gradlew build" once first.');
    process.exit(2);
  }
  return path.join(dir, hit);
}

const JAR = mcClassesJar();
const MIXIN_SRC = rel('src', 'main', 'java', 'com', 'tamamo', 'apotheosisexpansion', 'mixin', 'NameTagItemMixin.java');

const runJavap = (...args) => execFileSync(JAVA, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });

let fails = 0;
const ok = (state, message) => { if (!check(state, message)) fails++; };

// ---- 1. target class
console.log('1) target class');
const nameTag = runJavap('-p', '-s', '-classpath', JAR, 'net.minecraft.world.item.NameTagItem');
ok(nameTag.includes('class net.minecraft.world.item.NameTagItem'), 'NameTagItem resolved');

// ---- 2. target method + descriptor
console.log('2) interactLivingEntity');
const wantedDesc = '(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;'
  + 'Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;';
ok(nameTag.includes('interactLivingEntity'), 'interactLivingEntity declared');
ok(nameTag.includes(wantedDesc), 'interactLivingEntity has the expected descriptor');
ok(nameTag.includes('Lnet/minecraft/world/InteractionResult;'), 'returns InteractionResult');

/** Whether the target method returns a value; this decides the injector callback type. */
const returnsValue = !wantedDesc.endsWith(')V');

// ---- body analysis: offsets, invokes and returns
const body = runJavap('-p', '-c', '-classpath', JAR, 'net.minecraft.world.item.NameTagItem');
const methodBody = body.slice(body.indexOf('interactLivingEntity'));

/**
 * Bytecode lines in method order.
 * javap prints "  <offset>: <opcode> <operands>  // <comment>", where the comment for an invoke looks
 * like "Method net/minecraft/world/item/ItemStack.shrink:(I)V". The owner/name are parsed out of that
 * comment, because the operand column only carries a constant-pool index.
 */
const ops = [];
for (const line of methodBody.split(/\r?\n/)) {
  const head = line.match(/^\s*(\d+): (\w+)/);
  if (!head) continue;
  const entry = { offset: Number(head[1]), opcode: head[2], owner: null, name: null };
  const ownerAndName = line.match(/\/\/\s+(?:Method|InterfaceMethod|Field)\s+([\w/$]+)\.([\w$<>]+)/);
  if (ownerAndName) {
    entry.owner = ownerAndName[1];
    entry.name = ownerAndName[2];
  }
  ops.push(entry);
}

// ---- 3. more than one return => TAIL is wrong here
console.log('3) return count (why not TAIL)');
const returns = ops.filter(o => o.opcode === 'areturn' || o.opcode === 'ireturn' || o.opcode === 'return');
ok(returns.length > 1,
  'method has ' + returns.length + ' returns, so @At("TAIL") would land on the last (PASS) branch');
ok(returns.length === 2, 'expected exactly 2 returns (success + PASS)');

// ---- 4. anchor is unique
console.log('4) injection anchor');
const anchors = ops.filter(o => o.opcode === 'invokevirtual' && o.owner === 'net/minecraft/world/item/ItemStack' && o.name === 'shrink');
ok(anchors.length === 1, 'ItemStack.shrink occurs exactly once (' + anchors.length + ') - injection is unambiguous');
const anchorOffset = anchors[0] ? anchors[0].offset : -1;

// ---- 5. anchor is on the success branch
console.log('5) anchor sits on the success branch');
const firstReturn = returns.length ? returns[0].offset : -1;
const persistence = ops.find(o => o.name === 'setPersistenceRequired');
const setCustom = ops.find(o => o.name === 'setCustomName');

ok(!!setCustom, 'setCustomName is called');
ok(!!persistence, 'setPersistenceRequired is called');
ok(!!setCustom && !!persistence && persistence.offset > setCustom.offset,
  'mob is named before persistence is set');
ok(!!persistence && anchorOffset > persistence.offset,
  'anchor runs after the mob was named (' + anchorOffset + ' > ' + persistence.offset + ')');
ok(anchorOffset > 0 && firstReturn > anchorOffset,
  'anchor runs before the success return (' + anchorOffset + ' < ' + firstReturn + ')');

// The PASS branch must come after the success branch, otherwise the reasoning about TAIL is wrong.
ok(returns.length === 2 && returns[0].offset < returns[1].offset,
  'success return precedes the PASS return in bytecode order');

// ---- 6. mixin source agrees
console.log('6) mixin source');
const rawSrc = fs.readFileSync(MIXIN_SRC, 'utf8');
// Strip comments first: the class Javadoc explains why TAIL is wrong and would otherwise be
// indistinguishable from actually using it.
const src = rawSrc
  .replace(/\/\*[\s\S]*?\*\//g, '')   // block comments (incl. Javadoc)
  .replace(/\/\/[^\n]*/g, '');        // line comments
ok(src.includes('value = "INVOKE"'), 'mixin injects at an INVOKE anchor');
ok(src.includes('Lnet/minecraft/world/item/ItemStack;shrink(I)V'), 'mixin names the ItemStack.shrink target');
ok(!/value\s*=\s*"TAIL"/.test(src) && !src.includes('@At("TAIL")'),
  'mixin does not use TAIL (would hit the PASS return)');
ok(!/value\s*=\s*"RETURN"/.test(src), 'mixin does not rely on RETURN ordinals');
ok(!src.includes('setReturnValue'), 'mixin does not alter the returned value');

// The callback type is dictated by the TARGET METHOD's return type, not by the injection point.
// This tripped the build up twice: an INVOKE anchor inside a value-returning method still needs
// CallbackInfoReturnable, and Mixin rejects a plain CallbackInfo with
// "CallbackInfoReturnable is required" at apply time.
ok(returnsValue, 'target method returns a value, so the callback must be returnable');
if (returnsValue) {
  ok(/CallbackInfoReturnable\s*<\s*InteractionResult\s*>\s+\w+\s*\)/.test(src),
    'mixin declares CallbackInfoReturnable<InteractionResult>');
  ok(!/CallbackInfo\s+\w+\s*\)/.test(src),
    'mixin does not declare a plain CallbackInfo (fails at apply time on a value-returning method)');
} else {
  ok(/CallbackInfo\s+\w+\s*\)/.test(src), 'mixin declares plain CallbackInfo (method returns void)');
}

// ---- 7. scoreboard tag API used by the handler
console.log('7) Entity tag API');
const entity = runJavap('-p', '-s', '-classpath', JAR, 'net.minecraft.world.entity.Entity');
ok(entity.includes('public boolean addTag(java.lang.String)'), 'Entity.addTag(String) exists');
ok(entity.includes('public java.util.Set<java.lang.String> getTags()'), 'Entity.getTags() exists');

console.log('');
if (fails) {
  console.log('FAILED: ' + fails + ' check(s)');
  process.exit(1);
}
console.log('All checks passed.');
