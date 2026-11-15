// Verifies the spawner-exclusion predicate against the Java source and the real data folders.
//
// The predicate decides which registry ids belong to this mod. It used to match a name prefix; the
// data folders are now plain mod names, so it matches an explicit folder allow-list instead. The
// test therefore has two jobs: the JS mirror must behave like the Java, and the allow-list must
// cover every folder that actually exists (otherwise a whole compat group would leak into boss
// spawner blocks).
const fs = require('fs');
const path = require('path');

const JAVA = 'src/main/java/com/tamamo/apotheosisexpansion/compat/InvaderSpawnerFilter.java';
const ROOT = 'src/main/resources/data/apotheosis/apothic_invaders';

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const src = fs.readFileSync(JAVA, 'utf8');

// ---- 1. read the folder set out of the Java source
const setMatch = src.match(/OWN_FOLDERS = java\.util\.Set\.of\(([\s\S]*?)\);/);
ok(setMatch !== null, 'OWN_FOLDERS declared in the Java source');
const folders = setMatch
  ? [...setMatch[1].matchAll(/"([^"]+)"/g)].map(m => m[1])
  : [];
console.log('  folders: ' + folders.join(', '));

// ---- 2. mirror the predicate exactly as Java implements it
function isOwnPath(p) {
  if (!p.startsWith('apothic_invaders/')) return false;
  const rest = p.substring('apothic_invaders/'.length);
  const slash = rest.indexOf('/');
  if (slash < 0) return false;
  return folders.includes(rest.substring(0, slash));
}

function isOurs(id) {
  const [ns, ...rest] = id.split(':');
  if (ns !== 'apotheosis') return false;
  return isOwnPath(rest.join(':'));
}

// ---- 3. the Java must actually route through the helper
ok(src.includes('isOwnPath(id.getPath())'), 'isAddedByThisMod uses isOwnPath');
ok(/private static boolean isOwnPath\(String path\)/.test(src), 'isOwnPath is defined');
ok(src.includes('OWN_FOLDERS.contains('), 'isOwnPath consults the folder set');

// ---- 4. every folder on disk must be in the allow-list, and vice versa
const onDisk = fs.readdirSync(ROOT).filter(d => fs.statSync(path.join(ROOT, d)).isDirectory());
for (const d of onDisk) {
  ok(folders.includes(d), 'folder on disk is covered by OWN_FOLDERS: ' + d);
}
for (const f of folders) {
  ok(onDisk.includes(f), 'OWN_FOLDERS entry exists on disk: ' + f);
}

// ---- 5. behaviour table
const CASES = [
  ['apotheosis:apothic_invaders/thermal/basalz_peaks', true],
  ['apotheosis:apothic_invaders/mekanism/baby_skeleton', true],
  ['apotheosis:apothic_invaders/ars_nouveau/wilden_hunter', true],
  ['apotheosis:apothic_invaders/allthemodium/piglich', true],
  ['apotheosis:apothic_invaders/vanilla_bosses/warden', true],
  // Apotheosis' own folders must never be excluded
  ['apotheosis:apothic_invaders/overworld/zombie', false],
  ['apotheosis:apothic_invaders/the_nether/wither_skeleton', false],
  ['apotheosis:apothic_invaders/the_end/enderman', false],
  ['apotheosis:apothic_invaders/twilight/hydra', false],
  // structural edge cases
  ['minecraft:apothic_invaders/thermal/x', false],
  ['apotheosis:thermal/x', false],
  ['apotheosis:apothic_invaders/thermal', false],
  ['apotheosis:apothic_invaders/unknown_pack/x', false],
  ['apotheosis:apothic_invaders/thermal_generated/x', false],
];

for (const [id, expected] of CASES) {
  ok(isOurs(id) === expected, (expected ? 'ours     : ' : 'not ours : ') + id);
}

console.log(fails === 0 ? '\nFILTER PREDICATE OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
