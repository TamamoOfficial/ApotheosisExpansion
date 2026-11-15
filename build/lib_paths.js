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

/** Resolves the Apotheosis jar from APOTH_JAR, falling back to the libs/ folder. */
function apothJar() {
  const fromEnv = optionalEnv('APOTH_JAR');
  if (fromEnv) return fromEnv;
  const libs = rel('libs');
  if (fs.existsSync(libs)) {
    const hit = fs.readdirSync(libs).find(f => /^Apotheosis-.*\.jar$/i.test(f));
    if (hit) return path.join(libs, hit);
  }
  return requireEnv('APOTH_JAR', 'Path to the Apotheosis jar, e.g. libs/Apotheosis-1.21.1-8.9.0.jar');
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
  return requireEnv('PLACEBO_JAR', 'Path to the Placebo jar, e.g. libs/Placebo-1.21.1-9.9.4.jar');
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
