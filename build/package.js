// Packages build/classes + src/main/resources into the distributable jar.
//
// Mirrors what `gradlew build` would produce, which this machine cannot run (native-platform.dll). The
// manifest carries the same attributes build.gradle declares, because NeoForge reads ${file.jarVersion}
// out of it for the mod's display version.
//
// Usage:  node build/package.js
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { ROOT, rel } = require('./lib_paths');

const CLASSES = rel('build', 'classes');
const RESOURCES = rel('src', 'main', 'resources');
const LIBS = rel('build', 'libs');
const props = fs.readFileSync(rel('gradle.properties'), 'utf8');

const read = (key) => {
  const match = props.match(new RegExp('^' + key + '=(.*)$', 'm'));
  if (!match) throw new Error(key + ' missing from gradle.properties');
  return match[1].trim();
};

const version = read('modVersion');
const mc = read('mcVersion');
const name = 'ApotheosisExpansion-' + mc + '-neoforge-' + version + '.jar';

if (!fs.existsSync(CLASSES) || fs.readdirSync(CLASSES).length === 0) {
  throw new Error('build/classes is empty - run "node build/compile.js" first.');
}

// Staging directory: resources first, then classes over them, so a stale class can never shadow a resource.
const STAGE = rel('build', 'jarstage');
fs.rmSync(STAGE, { recursive: true, force: true });
fs.mkdirSync(STAGE, { recursive: true });
fs.cpSync(RESOURCES, STAGE, { recursive: true });
fs.cpSync(CLASSES, STAGE, { recursive: true });

const manifest = [
  'Manifest-Version: 1.0',
  'Specification-Title: apotheosis_expansion',
  'Implementation-Title: ApotheosisExpansion',
  'Implementation-Version: ' + version,
  '',
].join('\r\n');

const manifestPath = rel('build', 'MANIFEST.MF');
fs.writeFileSync(manifestPath, manifest);

fs.mkdirSync(LIBS, { recursive: true });
const jar = path.join(LIBS, name);
fs.rmSync(jar, { force: true });

// `jar` is part of the JDK, so no zip library is needed and the manifest gets the classic formatting.
execFileSync('jar', ['--create', '--file', jar, '--manifest', manifestPath, '-C', STAGE, '.'], {
  stdio: 'inherit',
  cwd: ROOT,
});

const size = (fs.statSync(jar).size / 1024).toFixed(0);
console.log('packaged: ' + path.relative(ROOT, jar) + ' (' + size + ' KB)');
