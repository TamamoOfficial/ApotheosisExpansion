// Validates the documentation set:
//
//   README.md / README_en.md            module overview, links into docs/
//   docs/FEATURES.md / _en.md           implementation details
//   docs/CONFIG.md / _en.md             config keys, build notes, source layout
//
// Checks, in order of how easily they break silently:
//   1. every relative link resolves (README -> docs/, docs -> README, doc <-> doc)
//   2. every in-page anchor matches a real heading in that file
//   3. the language pair of each doc is complete and mirrors the other's section count
//   4. the config key table covers every key that exists in the source
const fs = require('fs');
const path = require('path');

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const DOCS = [
  { file: 'README.md', lang: 'zh', pair: 'README_en.md' },
  { file: 'README_en.md', lang: 'en', pair: 'README.md' },
  { file: 'docs/FEATURES.md', lang: 'zh', pair: 'docs/FEATURES_en.md' },
  { file: 'docs/FEATURES_en.md', lang: 'en', pair: 'docs/FEATURES.md' },
  { file: 'docs/CONFIG.md', lang: 'zh', pair: 'docs/CONFIG_en.md' },
  { file: 'docs/CONFIG_en.md', lang: 'en', pair: 'docs/CONFIG.md' },
];

/** GitHub heading slug. */
function slug(text) {
  return text
    .toLowerCase()
    .replace(/[`*_[\]()#.,:!?'"“”‘’]/g, '')
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s+/g, '-');
}

for (const doc of DOCS) {
  console.log('\n== ' + doc.file);
  if (!fs.existsSync(doc.file)) {
    ok(false, 'file exists');
    continue;
  }
  const src = fs.readFileSync(doc.file, 'utf8');
  const dir = path.dirname(doc.file);

  // ---- 1. relative links
  const links = [...src.matchAll(/\[([^\]]+)\]\(([^)]+)\)/g)]
    .map(m => m[2])
    .filter(h => !/^https?:/.test(h) && !h.startsWith('#'));

  let bad = 0;
  for (const href of new Set(links)) {
    const target = path.resolve(dir, href.split('#')[0]);
    if (!fs.existsSync(target)) {
      console.log('  FAIL broken link: ' + href);
      bad++;
      fails++;
    }
  }
  ok(bad === 0, 'all ' + new Set(links).size + ' relative links resolve');

  // ---- 2. anchors
  const headings = [...src.matchAll(/^#{1,5} (.+)$/gm)].map(m => slug(m[1].trim()));
  const anchors = new Set(headings);
  let badAnchors = 0;
  for (const a of new Set([...src.matchAll(/\]\(#([^)]+)\)/g)].map(m => m[1]))) {
    if (!anchors.has(a)) {
      console.log('  FAIL no heading for anchor #' + a);
      badAnchors++;
      fails++;
    }
  }
  ok(badAnchors === 0, 'in-page anchors resolve');

  // ---- 3. language pair
  ok(fs.existsSync(doc.pair), 'language pair exists: ' + doc.pair);
  ok(src.includes('(' + path.basename(doc.pair) + ')'), 'links to its language pair');
}

// ---- 4. section parity within each pair
console.log('\n== section parity');
for (const [a, b] of [
  ['README.md', 'README_en.md'],
  ['docs/FEATURES.md', 'docs/FEATURES_en.md'],
  ['docs/CONFIG.md', 'docs/CONFIG_en.md'],
]) {
  const ca = (fs.readFileSync(a, 'utf8').match(/^## /gm) || []).length;
  const cb = (fs.readFileSync(b, 'utf8').match(/^## /gm) || []).length;
  ok(ca === cb, a + ' vs ' + b + ': ' + ca + ' / ' + cb + ' level-2 sections');
}

// ---- 5. every source config key appears in both config docs
console.log('\n== config key coverage');
const cfg = fs.readFileSync('src/main/java/com/tamamo/apotheosisexpansion/Config.java', 'utf8');
const literal = [...cfg.matchAll(/\.define(?:InRange|ListAllowEmpty)?\("([a-z_0-9]+)"/g)].map(m => m[1]);
const ent = [...fs.readFileSync('src/main/java/com/tamamo/apotheosisexpansion/compat/EntityInvaderEnabled.java', 'utf8')
  .matchAll(/m\.put\("[^"]+",\s*"([^"]+)"\);/g)].map(m => m[1]);

const zhCfg = fs.readFileSync('docs/CONFIG.md', 'utf8');
const enCfg = fs.readFileSync('docs/CONFIG_en.md', 'utf8');
for (const k of new Set([...literal, ...ent])) {
  ok(zhCfg.includes('`' + k + '`'), 'zh config doc lists ' + k);
  ok(enCfg.includes('`' + k + '`'), 'en config doc lists ' + k);
}

// ---- 6. the README must stay short (it is an overview, details live in docs/)
console.log('\n== README length');
for (const f of ['README.md', 'README_en.md']) {
  const lines = fs.readFileSync(f, 'utf8').split('\n').length;
  ok(lines < 150, f + ' stays an overview (' + lines + ' lines)');
}

console.log(fails === 0 ? '\nDOCS OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
