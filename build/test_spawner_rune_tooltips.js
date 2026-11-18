// Behaviour test for the spawner-rune tooltip formatter.
//
// The Java formatter cannot be run here (no game launch), so the same rules are mirrored below and driven with the
// REAL recipes out of the Apotheosis jar. That is the point: it checks the decisions against shipped data rather than
// against invented samples -
//
//   * the four upgrade runes print five plain "stat: value" lines, in recipe order
//   * set + boolean prints "Enables/Disables X", never "X: true"
//   * additive runes print a signed number, and a clamped one also prints its bound
//   * a negative additive value prints a single minus, not two
//   * off-hand (inverse) recipes are skipped, so a rune never describes itself as both on and off
//
// Run from the project root:  node build/test_spawner_rune_tooltips.js
const fs = require('fs');
const { execFileSync } = require('child_process');
const { apothJar } = require('./lib_paths');

let fails = 0;
const ok = (c, m) => { console.log((c ? '  PASS ' : '  FAIL ') + m); if (!c) fails++; };

const JAR = apothJar();
const readJson = (entry) => JSON.parse(execFileSync('unzip', ['-p', JAR, entry], { encoding: 'utf8' }));

// ============================================================================================
// Mirror of SpawnerRuneTooltips.describe()/additive()/clamp().
//
// `format` stands in for SpawnerStat#formatValue: the real one renders ticks as plain integers, percentages as
// "12.3%" and echoing levels as roman numerals, so the important property to mirror is that it is the *stat* that
// decides the text and the formatter must not add its own units.
// ============================================================================================

/** Mirrors StatModifier#getFormattedValue for the fixed-value cases the shipped recipes use. */
function formatValue(value) {
  if (typeof value === 'boolean') return String(value);
  return String(value);
}

/** Mirrors the roman-numeral formatting of LevelStat, used by the echoing rune. */
function formatLevel(value) {
  const ROMAN = ['0', 'I', 'II', 'III', 'IV', 'V'];
  return ROMAN[Math.abs(value)] || String(value);
}

/**
 * Mirrors SpawnerRuneTooltips#describe.
 *
 * @param change one entry of stat_changes
 * @param statName the translated stat name (here: the lang key's en_us text, or the id as a fallback)
 */
function describe(change, statName) {
  const mode = change.mode || 'add';
  const value = change.type === 'apothic_spawners:echoing' ? formatLevel(change.value) : formatValue(change.value);

  if (mode === 'set' && typeof change.value === 'boolean') {
    return (change.value ? 'enables' : 'disables') + ':' + statName;
  }
  if (mode === 'set') {
    return 'set:' + statName + '=' + value;
  }

  // additive
  let text = value;
  let sign = '';
  if (typeof change.value === 'number' && change.value > 0) sign = '+';
  else if (typeof change.value === 'number' && change.value < 0) {
    sign = '-';
    text = text.startsWith('-') ? text.substring(1) : text;
  }

  let line = sign + text + ' ' + statName;

  const bounds = [];
  if (change.max !== undefined) bounds.push('max ' + statFormat(change, change.max));
  if (change.min !== undefined) bounds.push('min ' + statFormat(change, change.min));
  if (bounds.length) line += ' (' + bounds.join(' ') + ')';

  return line;
}

/** Mirrors SpawnerRuneTooltips#format: the stat decides the text of a bound too. */
function statFormat(change, value) {
  return change.type === 'apothic_spawners:echoing' ? formatLevel(value) : formatValue(value);
}

/** Mirrors the scan: main-hand match, off-hand variants skipped. */
function linesFor(recipe, statNames) {
  if (recipe.offhand) return [];
  return (recipe.stat_changes || []).map(c => describe(c, statNames[c.type] || c.type));
}

// ---------------------------------------------------------------------------------------------
// Stat names as the mod's own lang files define them (en_us of Apothic Spawners ships these; the
// tooltip calls SpawnerStat#name(), which resolves the same keys).
// ---------------------------------------------------------------------------------------------
const STAT_NAMES = {
  'apothic_spawners:min_delay': 'Min Spawn Delay',
  'apothic_spawners:max_delay': 'Max Spawn Delay',
  'apothic_spawners:spawn_count': 'Spawn Count',
  'apothic_spawners:max_nearby_entities': 'Max Nearby Entities',
  'apothic_spawners:req_player_range': 'Required Player Range',
  'apothic_spawners:spawn_range': 'Spawn Range',
  'apothic_spawners:initial_health': 'Initial Health',
  'apothic_spawners:ignore_players': 'Ignore Players',
  'apothic_spawners:ignore_conditions': 'Ignore Conditions',
  'apothic_spawners:redstone_control': 'Redstone Control',
  'apothic_spawners:ignore_light': 'Ignore Light',
  'apothic_spawners:no_ai': 'No AI',
  'apothic_spawners:silent': 'Silent',
  'apothic_spawners:youthful': 'Youthful',
  'apothic_spawners:burning': 'Burning',
  'apothic_spawners:echoing': 'Echoing',
};

// ---------------------------------------------------------------- 1. the four upgrade runes
console.log('1) upgrade runes print a plain value per stat, in recipe order');
{
  const expected = {
    frontier: [150, 600, 7, 12, 24],
    ascent: [100, 400, 10, 18, 32],
    summit: [50, 200, 13, 24, 40],
    pinnacle: [20, 20, 16, 32, 48],
  };
  const statOrder = ['min_delay', 'max_delay', 'spawn_count', 'max_nearby_entities', 'req_player_range'];

  for (const [tier, values] of Object.entries(expected)) {
    const recipe = readJson('data/apotheosis/recipe/spawner_modifiers/tier/' + tier + '.json');
    const lines = linesFor(recipe, STAT_NAMES);

    ok(lines.length === 5, tier + ': five lines');
    ok(lines.every(l => l.startsWith('set:')), tier + ': every line is a "set"');

    // The printed value must be exactly what the recipe says - not a copy of it living in code.
    const printed = lines.map(l => Number(l.slice(l.indexOf('=') + 1)));
    ok(JSON.stringify(printed) === JSON.stringify(values),
      tier + ': values come from the recipe (' + printed.join('/') + ')');

    // ...and the stat order follows the recipe, so a reordered pack stays readable.
    ok(JSON.stringify(recipe.stat_changes.map(s => s.type.replace('apothic_spawners:', ''))) === JSON.stringify(statOrder),
      tier + ': stats are in the shipped order');
  }
}

// ---------------------------------------------------------------- 2. booleans
console.log('\n2) a boolean stat reads as a state, never as "true/false"');
{
  const recipe = readJson('data/apotheosis/recipe/spawner_modifiers/ignore_light.json');
  const lines = linesFor(recipe, STAT_NAMES);
  ok(lines.length === 1, 'ignore_light has one stat change');
  ok(lines[0] === 'enables:Ignore Light', 'prints "Enables Ignore Light" (got "' + lines[0] + '")');
  ok(!lines[0].includes('true'), 'the raw boolean never leaks into the line');

  // The inverse recipe sets it back to false; that rune-shaped recipe must read as "Disables", and the pair must not
  // be produced by the same recipe entry (the scan skips off-hand variants entirely).
  const inverse = readJson('data/apotheosis/recipe/spawner_modifiers/_inverse/ignore_light.json');
  ok(linesFor(inverse, STAT_NAMES).length === 0, 'the off-hand inverse variant produces no lines');
  ok(inverse.stat_changes[0].value === false, 'the inverse recipe really carries false, so skipping it loses nothing');
}

// ---------------------------------------------------------------- 3. additive values and clamps
console.log('\n3) additive values carry a sign, and clamps are shown');
{
  const range = readJson('data/apotheosis/recipe/spawner_modifiers/spawn_range.json');
  const lines = linesFor(range, STAT_NAMES);
  ok(lines[0] === '+2 Spawn Range (max 32)', 'spawn_range -> "+2 Spawn Range (max 32)" (got "' + lines[0] + '")');

  // echoing is a LevelStat: both the value and its bound render as roman numerals, exactly as the spawner block
  // shows them, so the tooltip and the block agree rather than the tooltip inventing a numeric scale.
  const echo = readJson('data/apotheosis/recipe/spawner_modifiers/echoing.json');
  ok(linesFor(echo, STAT_NAMES)[0] === '+I Echoing (max III)',
    'echoing -> "+I Echoing (max III)" (got "' + linesFor(echo, STAT_NAMES)[0] + '")');

  // A negative additive with a minimum: initial_health is -0.16 with min 0.2.
  const health = readJson('data/apotheosis/recipe/spawner_modifiers/initial_health.json');
  const healthLine = linesFor(health, STAT_NAMES)[0];
  ok(healthLine.startsWith('-0.16 Initial Health'), 'negative value -> "-0.16 Initial Health" (got "' + healthLine + '")');
  ok(!healthLine.includes('--'), 'the sign is not doubled');
  ok(healthLine.includes('(min 0.2)'), 'the minimum is appended');

  // The inverse of a level stat subtracts one; formatValue already prints "-I" for that, so the formatter must strip
  // the sign before adding its own.
  const inverseEcho = readJson('data/apotheosis/recipe/spawner_modifiers/_inverse/echoing.json');
  const inverseLine = describe(inverseEcho.stat_changes[0], STAT_NAMES['apothic_spawners:echoing']);
  ok(inverseLine === '-I Echoing (min 0)', 'inverse echoing -> "-I Echoing (min 0)" (got "' + inverseLine + '")');
}

// ---------------------------------------------------------------- 4. off-hand variants never describe a rune
console.log('\n4) the scan describes only the main-hand-alone form');
{
  const entries = execFileSync('unzip', ['-l', JAR], { encoding: 'utf8' })
    .split('\n')
    .map(l => l.trim().split(/\s+/).pop())
    .filter(n => n && n.includes('spawner_modifiers/') && n.endsWith('.json'));

  let withOffhand = 0;
  let withoutOffhand = 0;
  for (const entry of entries) {
    const json = readJson(entry);
    if (json.type !== 'apothic_spawners:spawner_modifier') continue;
    if (json.offhand) {
      withOffhand++;
      ok(linesFor(json, STAT_NAMES).length === 0, entry + ' (off-hand) yields no lines');
    }
    else withoutOffhand++;
  }
  ok(withOffhand > 0, 'the pack does ship off-hand variants (' + withOffhand + ') to be skipped');
  ok(withoutOffhand > 0, 'and main-hand ones (' + withoutOffhand + ') to be described');
}

// ---------------------------------------------------------------- 5. no line can be empty
console.log('\n5) every produced line is non-empty and single-line');
{
  const entries = execFileSync('unzip', ['-l', JAR], { encoding: 'utf8' })
    .split('\n')
    .map(l => l.trim().split(/\s+/).pop())
    .filter(n => n && n.includes('spawner_modifiers/') && n.endsWith('.json'));

  let total = 0;
  let bad = 0;
  for (const entry of entries) {
    const json = readJson(entry);
    if (json.type !== 'apothic_spawners:spawner_modifier') continue;
    for (const line of linesFor(json, STAT_NAMES)) {
      total++;
      if (!line.trim() || line.includes('\n')) {
        bad++;
        console.log('  FAIL bad line from ' + entry + ': ' + JSON.stringify(line));
      }
    }
  }
  ok(bad === 0, total + ' formatted lines are all non-empty one-liners');
  ok(total > 10, 'the corpus is big enough to be meaningful (' + total + ' lines)');
}

console.log(fails === 0 ? '\nSPAWNER RUNE FORMAT OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
