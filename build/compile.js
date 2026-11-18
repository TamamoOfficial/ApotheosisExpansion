// Local javac build for the development machine.
//
// Gradle cannot start here (native-platform.dll), so the mod is compiled and packaged by hand. This script
// does the three things the manual steps in docs/CONFIG.md describe, and nothing else:
//
//   1. resolves a JDK 21 javac (the mod targets Java 21)
//   2. builds a classpath from build/cp.txt (Gradle's resolved runtime classpath) plus the libs/*.jar set
//   3. compiles src/main/java into build/classes with -sourcepath pointed at an EMPTY dir
//
// Why the empty -sourcepath matters: without it javac's implicit source discovery finds the .java files that
// ship inside the dependency jars and recompiles them into the output directory - 1081 NeoForge classes ended
// up in the jar that way.
//
// Usage:  node build/compile.js          (or: node build/compile.js --package)
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { ROOT, rel } = require('./lib_paths');

const OUT = rel('build', 'classes');
const EMPTY = rel('build', '.emptysrc');

function findJavac() {
  const candidates = [
    process.env.JAVAC,
    'C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/bin/javac.exe',
    process.env.JAVA_HOME && path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'javac.exe' : 'javac'),
    'javac',
  ].filter(Boolean);

  for (const candidate of candidates) {
    try {
      const version = execFileSync(candidate, ['-version'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
      if (/javac\s+21/.test(version)) return candidate;
    }
    catch { /* try the next candidate */ }
  }
  throw new Error('No javac 21 found. Set JAVAC to a JDK 21 javac.');
}

/** Every .java under src/main/java, in a stable order. */
function sources(dir = rel('src', 'main', 'java'), acc = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) sources(full, acc);
    else if (entry.name.endsWith('.java')) acc.push(full);
  }
  return acc;
}

/**
 * Classpath: Gradle's resolved list plus every libs/*.jar.
 *
 * build/cp.txt is one line of `;`-separated absolute Windows paths. It is written by the Gradle `compileJava`
 * classpath dump and is git-ignored, so a fresh clone rebuilds it with `gradlew build` (or the deps in libs/).
 */
function classpath() {
  const parts = [];

  const cpFile = rel('cp.txt');
  if (fs.existsSync(cpFile)) {
    for (const entry of fs.readFileSync(cpFile, 'utf8').split(';')) {
      const trimmed = entry.trim().replace(/^\uFEFF/, '');
      if (trimmed && fs.existsSync(trimmed)) parts.push(trimmed);
    }
  }

  // libs/ holds the Apotheosis family when the machine is offline. Those jars must come first so their
  // classes win over any stale copy in the Gradle cache.
  const libs = rel('libs');
  const local = fs.existsSync(libs)
    ? fs.readdirSync(libs).filter(f => f.endsWith('.jar')).map(f => path.join(libs, f))
    : [];

  // Apothic Spawners is what the spawner-rune tooltips compile against. It is an optional runtime dependency,
  // so it is not in cp.txt (Gradle only put the runtime classpath there); a copy next to the project is used.
  const extras = ['ApothicSpawners-1.21.1-1.4.0.jar']
    .map(name => path.join(libs, name))
    .filter(p => fs.existsSync(p));

  return [...local, ...extras, ...parts].join(path.delimiter);
}

const javac = findJavac();
const files = sources();

// Start from an empty output directory. A previous `gradlew build` leaves build/classes/java/main/ behind, and
// copying build/classes wholesale would package those stale classes next to the fresh ones - the runtime then picks
// whichever copy the class loader finds first, which is how a "fixed" jar can keep running the old code.
fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(EMPTY, { recursive: true });

const args = [
  '-encoding', 'UTF-8',
  '-proc:none',
  '--release', '21',
  '-nowarn',
  '-sourcepath', EMPTY,
  '-classpath', classpath(),
  '-d', OUT,
  ...files,
];

console.log('javac     : ' + javac);
console.log('sources   : ' + files.length);
execFileSync(javac, args, { stdio: 'inherit', cwd: ROOT });
console.log('compiled  -> ' + OUT);
