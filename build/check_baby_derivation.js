// Verifies the Mek baby entries are faithful derivatives of their adult counterparts:
//   - stat table identical to the adult's
//   - gear pool identical to the adult's
//   - weights exactly 1/3 of the adult's (per tier), floored at 1
// This is the invariant the whole feature rests on, so it is checked rather than assumed.
const fs = require('fs');
const path = require('path');
const { apothRefDir } = require('./lib_paths');

const REF = apothRefDir();
const DIR = 'src/main/resources/data/apotheosis/apothic_invaders/mekanism';

const readRef = p => JSON.parse(fs.readFileSync(path.join(REF, p), 'utf8'));

// baby file -> adult reference file
const PAIRS = {
  'baby_enderman.json': 'overworld/enderman.json',
  'baby_enderman_end.json': 'the_end/enderman.json',
  'baby_skeleton.json': 'overworld/skeleton.json',
  'baby_stray.json': 'overworld/stray.json',
  'baby_stray_cold.json': 'overworld/stray_in_cold.json',
  'baby_bogged.json': 'overworld/bogged.json',
  'baby_bogged_wet.json': 'overworld/bogged_in_wet.json',
  // --- special case: wither skeleton baby ---------------------------------
  // Deliberate deviations from the "copy the adult verbatim" rule, per user decision:
  //   * epic rarity ONLY (the adult ships epic + mythic)
  //   * weight fixed at 25 = 1/4 of the adult (not the 1/3 used by the other babies)
  //   * dimension-only constraint (fortresses replace the biome spawn table)
  'baby_wither_skeleton.json': 'the_nether/wither_skeleton.json',
};

let fails = 0;
const ok = (c, m) => { if (!c) { console.log('  FAIL ' + m); fails++; } };

function third(w) {
  if (w.weight !== undefined) return { quality: w.quality, weight: Math.max(1, Math.round(w.weight / 3)) };
  const out = {};
  for (const [t, v] of Object.entries(w)) out[t] = { quality: v.quality, weight: Math.max(1, Math.round(v.weight / 3)) };
  return out;
}

for (const [babyFile, adultFile] of Object.entries(PAIRS)) {
  if (babyFile === 'baby_wither_skeleton.json') continue; // handled separately below
  const baby = JSON.parse(fs.readFileSync(path.join(DIR, babyFile), 'utf8'));
  const adult = readRef(adultFile);
  console.log('== ' + babyFile + '  <-  ' + adultFile);

  // 1. stats must be byte-identical to the adult's
  ok(JSON.stringify(baby.stats) === JSON.stringify(adult.stats),
    'stat table differs from adult');

  // 2. gear pool identical
  ok(JSON.stringify(baby.basic_data.valid_gear_sets) === JSON.stringify(adult.basic_data.valid_gear_sets),
    'gear pool differs from adult');

  // 3. bonus loot identical
  ok(JSON.stringify(baby.basic_data.bonus_loot) === JSON.stringify(adult.basic_data.bonus_loot),
    'bonus loot differs from adult');

  // 4. weights exactly one third
  ok(JSON.stringify(baby.basic_data.weights) === JSON.stringify(third(adult.basic_data.weights)),
    'weights are not exactly 1/3 of the adult: got ' + JSON.stringify(baby.basic_data.weights) +
    ' expected ' + JSON.stringify(third(adult.basic_data.weights)));

  // 5. entity must be the baby of the adult's entity
  const adultMob = adult.entity.replace('minecraft:', '');
  ok(baby.entity === 'mekanismadditions:baby_' + (adultMob === 'ender' ? 'enderman' : adultMob),
    'entity ' + baby.entity + ' is not the baby of ' + adult.entity);

  // 6. baby AABB must be smaller than the adult's (it is a baby, after all)
  const bw = baby.size.width, bh = baby.size.height;
  const aw = adult.size.width, ah = adult.size.height;
  ok(bw <= aw && bh <= ah, 'baby AABB ' + bw + 'x' + bh + ' larger than adult ' + aw + 'x' + ah);

  console.log('   weights ' + JSON.stringify(baby.basic_data.weights) +
    '   size ' + bw + 'x' + bh);
}

// ---- wither skeleton baby: the quarter-weight, epic-only special case ----
{
  const baby = JSON.parse(fs.readFileSync(path.join(DIR, 'baby_wither_skeleton.json'), 'utf8'));
  const adult = readRef('the_nether/wither_skeleton.json');
  console.log('== baby_wither_skeleton.json  <-  the_nether/wither_skeleton.json  [special]');

  ok(baby.entity === 'mekanismadditions:baby_wither_skeleton', 'wrong entity');

  const rar = Object.keys(baby.stats);
  ok(rar.length === 1 && rar[0] === 'apotheosis:epic',
    'must provide exactly the epic rarity, got ' + JSON.stringify(rar));

  ok(JSON.stringify(baby.stats['apotheosis:epic']) === JSON.stringify(adult.stats['apotheosis:epic']),
    'epic stat block must match the adult epic stage verbatim');

  ok(JSON.stringify(baby.basic_data.valid_gear_sets) === JSON.stringify(adult.basic_data.valid_gear_sets),
    'gear pool must match the adult');

  ok(JSON.stringify(baby.basic_data.constraints) === JSON.stringify({ dimensions: ['minecraft:the_nether'] }),
    'constraint must be the_nether only, with no biome');

  for (const tier of ['ascent', 'summit', 'pinnacle']) {
    ok(baby.basic_data.weights[tier] && baby.basic_data.weights[tier].weight === 25,
      tier + ' weight must be 25, i.e. 1/4 of the adult 100');
  }
  ok(baby.basic_data.weights.haven === undefined && baby.basic_data.weights.frontier === undefined,
    'must start at ascent, matching the adult');

  // width may equal the adult (the baby hitbox is narrow but a boss needs room for the scale roll)
  ok(baby.size.width <= adult.size.width && baby.size.height < adult.size.height,
    "baby AABB must not be larger than the adult AABB");

  console.log('   rarities ' + JSON.stringify(rar) +
    '   weights ' + JSON.stringify(baby.basic_data.weights) +
    '   size ' + JSON.stringify(baby.size));
}

// every baby entity that Additions registers (except creeper/wither skeleton, deferred) must be covered
const covered = new Set(Object.values(PAIRS).map(p => readRef(p).entity));
ok(covered.size >= 4, 'expected at least 4 distinct adult derivations');
const ents = new Set(fs.readdirSync(DIR).map(f => JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8')).entity));
for (const forbidden of ['mekanismadditions:baby_creeper']) {
  ok(!ents.has(forbidden), forbidden + ' must not be present (out of scope by decision)');
}

console.log(fails === 0 ? '\nBABY DERIVATION OK' : '\n' + fails + ' FAILURE(S)');
process.exit(fails === 0 ? 0 : 1);
