// Shared path and tool resolution for the validation scripts.
//
// The scripts originally hard-coded absolute paths from the development machine. They now resolve
// everything relative to the repository root, and any external input (the Apotheosis jar, the
// NeoForge sources jar, javap) is taken from an environment variable so the checks fail with a clear
// message instead of silently skipping.
const fs = require('fs');
const path = require('path');

/** Repository root: this file lives in <root>/build/. */
const ROOT = path.resolve(__dirname, '..');

/** Project-relative path helper. */
const rel = (...parts) => path.join(ROOT, ...parts);

/**
 * Reads an environment variable, exiting with an actionable message when it is missing.
 * Used for inputs that cannot live in the repository.
 */
function requireEnv(name, hint) {
  const value = process.env[name];
  if (!value || !value.trim()) {
    console.error('');
    console.error('MISSING ENVIRONMENT VARIABLE: ' + name);
    if (hint) console.error('  ' + hint);
    console.error('  See build/README.md for how to set it.');
    console.error('');
    process.exit(2);
  }
  return value.trim();
}

/** Like requireEnv but returns null instead of exiting, for optional inputs. */
function optionalEnv(name) {
  const v = process.env[name];
  return v && v.trim() ? v.trim() : null;
}

/**
 * Finds a jar inside the Gradle dependency cache.
 *
 * A fresh clone has no libs/ folder: build.gradle resolves the Apotheosis family from Shadow's
 * Maven, and Gradle stores what it downloads here. Searching the cache means the validators work
 * right after the first `gradlew build` with no extra setup.
 *
 * @param {string} prefix artifact name prefix, e.g. "Apotheosis-1.21.1"
 * @returns {string|null} the jar path, or null when nothing matches
 */
function gradleCacheJar(prefix) {
  const roots = [
    path.join(require('os').homedir(), '.gradle', 'caches', 'modules-2', 'files-2.1'),
    rel('.gradle', 'caches', 'modules-2', 'files-2.1'),
  ];
  for (const root of roots) {
    if (!fs.existsSync(root)) continue;
    const hits = [];
    const walk = (dir, depth) => {
      if (depth > 6 || hits.length > 4) return;
      let entries;
      try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { return; }
      for (const e of entries) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) walk(full, depth + 1);
        else if (e.name.startsWith(prefix) && e.name.endsWith('.jar')) hits.push(full);
      }
    };
    walk(root, 0);
    if (hits.length) return hits[0];
  }
  return null;
}

/** Resolves the Apotheosis jar: env var, then libs/, then the Gradle cache. */
function apothJar() {
  const fromEnv = optionalEnv('APOTH_JAR');
  if (fromEnv) return fromEnv;

  const libs = rel('libs');
  if (fs.existsSync(libs)) {
    const hit = fs.readdirSync(libs).find(f => /^Apotheosis-.*\.jar$/i.test(f));
    if (hit) return path.join(libs, hit);
  }

  const cached = gradleCacheJar('Apotheosis-');
  if (cached) return cached;

  return requireEnv('APOTH_JAR',
    'Run "gradlew build" once so Gradle downloads it, or set APOTH_JAR to the Apotheosis jar. '
    + 'Manual download: https://maven.shadowsoffire.dev/releases/dev/shadowsoffire/Apotheosis/');
}

/** Resolves the Placebo jar the same way. */
function placeboJar() {
  const fromEnv = optionalEnv('PLACEBO_JAR');
  if (fromEnv) return fromEnv;

  const libs = rel('libs');
  if (fs.existsSync(libs)) {
    const hit = fs.readdirSync(libs).find(f => /^Placebo-.*\.jar$/i.test(f));
    if (hit) return path.join(libs, hit);
  }

  const cached = gradleCacheJar('Placebo-');
  if (cached) return cached;

  return requireEnv('PLACEBO_JAR',
    'Run "gradlew build" once so Gradle downloads it, or set PLACEBO_JAR to the Placebo jar. '
    + 'Manual download: https://maven.shadowsoffire.dev/releases/dev/shadowsoffire/Placebo/');
}

/** javap: from JAVAP, else the one on PATH, else derived from JAVA_HOME. */
function javap() {
  const fromEnv = optionalEnv('JAVAP');
  if (fromEnv) return fromEnv;
  if (process.env.JAVA_HOME) {
    const exe = process.platform === 'win32' ? 'javap.exe' : 'javap';
    const candidate = path.join(process.env.JAVA_HOME, 'bin', exe);
    if (fs.existsSync(candidate)) return candidate;
  }
  return 'javap'; // rely on PATH; execFileSync will report ENOENT clearly enough
}

/** NeoForge sources jar, for checks that read vanilla sources. */
function mcSourcesJar() {
  const fromEnv = optionalEnv('MC_SOURCES_JAR');
  if (fromEnv) return fromEnv;
  // common moddev artifact locations
  const candidates = [
    rel('build', 'moddev', 'artifacts'),
    rel('..', 'mc1.21.1-neoforge', 'build', 'moddev', 'artifacts'),
  ];
  for (const dir of candidates) {
    if (!fs.existsSync(dir)) continue;
    const hit = fs.readdirSync(dir).find(f => /neoforge-.*-sources\.jar$/i.test(f));
    if (hit) return path.join(dir, hit);
  }
  return requireEnv('MC_SOURCES_JAR', 'Path to neoforge-*-sources.jar (a NeoForge moddev artifact)');
}

/** Reference data extracted from the Apotheosis jar, used only by the one-off generators. */
function apothRefDir() {
  const fromEnv = optionalEnv("APOTH_REF_DIR");
  if (fromEnv) return fromEnv;

  // Common location: this project sometimes unpacks the reference data into a temp folder.
  const guesses = [
    path.join(require("os").tmpdir(), "apoth2", "data", "apotheosis", "apothic_invaders"),
  ];
  for (const g of guesses) {
    if (fs.existsSync(g)) return g;
  }

  return requireEnv('APOTH_REF_DIR',
    'Extract data/apotheosis/apothic_invaders from the Apotheosis jar, then point this at it.');
}

/** Data pack root inside the repo. */
const INVADERS = rel('src', 'main', 'resources', 'data', 'apotheosis', 'apothic_invaders');

/** Prints a PASS/FAIL line and returns whether it passed. */
function check(state, message) {
  console.log((state ? '  PASS ' : '  FAIL ') + message);
  return !!state;
}

module.exports = {
  ROOT, rel, requireEnv, optionalEnv,
  apothJar, placeboJar, javap, mcSourcesJar, apothRefDir,
  INVADERS, check,
};
