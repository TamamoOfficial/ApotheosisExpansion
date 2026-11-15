// Computes: per how many naturally-spawned wither skeletons does one wither skeleton boss appear.
//
// Chain (from ApothMobEvents.trySpawnInvader):
//   1. must be NATURAL/CHUNK_GENERATION + Monster          -> wither skeleton qualifies
//   2. rand <= spawnChances.get(worldTier)                 -> dimension must be the_nether
//   3. surfaceType.test(...)                               -> the_nether uses ANY (always passes)
//   4. weighted pick among ELIGIBLE invaders              -> Constraints.eval(ctx)
//
// Step 4 is where the "which boss" decision happens: the original mob is cancelled and the
// picked invader replaces it. So a wither skeleton becomes *the wither skeleton boss* only when
// the pick lands on the wither_skeleton entry.
//
// Therefore: P(boss | wither skeleton spawn) = P(invader roll) x P(pick = wither_skeleton)
const fs = require('fs');
const path = require('path');
const { rel, apothRefDir } = require('./lib_paths');

const REF = apothRefDir();
const MINE = [
  rel('src', 'main', 'resources', 'data', 'apotheosis', 'apothic_invaders', 'thermal'),
  rel('src', 'main', 'resources', 'data', 'apotheosis', 'apothic_invaders', 'mekanism'),
];

const items = [];
function walk(dir, tag) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) { walk(p, tag); continue; }
    const j = JSON.parse(fs.readFileSync(p, 'utf8'));
    const c = (j.basic_data && j.basic_data.constraints) || {};
    items.push({ name: tag + '/' + path.basename(p, '.json'), entity: j.entity,
                 dims: c.dimensions, biomes: c.biomes, weights: j.basic_data.weights });
  }
}
walk(REF, 'apoth');
for (const d of MINE) walk(d, d.includes('thermal') ? 'thermal' : 'mek');

// Nether fortress biome context: fortress generates in nether_wastes / soul_sand_valley /
// crimson_forest / warped_forest (NOT basalt_deltas). None of the nether-tagged invaders
// constrain biome, and basalt_deltas-only entries (our basalz_nether) do not match here.
const CONTEXT = { dim: 'minecraft:the_nether', biome: 'minecraft:nether_wastes', tags: [] };

const NETHER_CHANCES = { frontier: 0.025, ascent: 0.027, summit: 0.030, pinnacle: 0.035 };
const TIERS = ['frontier', 'ascent', 'summit', 'pinnacle'];

function weightFor(it, tier) {
  const t = it.weights;
  if (t.weight !== undefined) return t.weight;
  const e = t[tier];
  return e ? e.weight : 0;
}

function eligible(it) {
  const d = it.dims;
  if (d && d.length && !d.includes(CONTEXT.dim)) return false;
  const b = it.biomes;
  if (b !== undefined) {
    if (typeof b === 'string') {
      if (!b.startsWith('#')) return b === CONTEXT.biome;
      return false; // no conventional tag matches a plain nether biome here
    }
    if (Array.isArray(b)) return b.includes(CONTEXT.biome);
    return false;
  }
  return true;
}

console.log('=== 要塞内（下界）候选池 ===');
for (const tier of TIERS) {
  const pool = items.filter(it => eligible(it) && weightFor(it, tier) > 0);
  const total = pool.reduce((a, b) => a + weightFor(b, tier), 0);
  const wither = pool.find(p => p.entity === 'minecraft:wither_skeleton');
  const wWither = wither ? weightFor(wither, tier) : 0;
  console.log('\n-- ' + tier + ' (触发率 ' + (NETHER_CHANCES[tier] * 100).toFixed(1) + '%)');
  console.log('   候选 ' + pool.length + ' 个, 总权重 ' + total);
  for (const p of pool) console.log('     ' + p.entity.padEnd(40) + 'w=' + weightFor(p, tier));
  if (wWither > 0) {
    const share = wWither / total;
    const perSpawn = share * NETHER_CHANCES[tier];
    console.log('   → 凋灵骷髅占比 ' + (share * 100).toFixed(2) + '%');
    console.log('   → 每只凋灵骷髅成为 boss: ' + (perSpawn * 100).toFixed(4) + '%  =  1 / ' + Math.round(1 / perSpawn));
  } else {
    console.log('   → 凋灵骷髅在此档无权重，不会出现');
  }
}

// ---- what weight would give which target? ----
console.log('\n=== 若调整凋灵骷髅条目权重（其余条目权重不变）===');
console.log('权重    ascent      summit      pinnacle    (每 N 只出现 1 只 boss)');
for (const w of [10, 20, 33, 50, 75, 100, 150, 200, 300]) {
  const row = [];
  for (const tier of ['ascent', 'summit', 'pinnacle']) {
    const pool = items.filter(it => eligible(it) && weightFor(it, tier) > 0 && it.entity !== 'minecraft:wither_skeleton');
    const others = pool.reduce((a, b) => a + weightFor(b, tier), 0);
    const p = (w / (others + w)) * NETHER_CHANCES[tier];
    row.push('1/' + Math.round(1 / p));
  }
  console.log(String(w).padEnd(7) + row[0].padEnd(12) + row[1].padEnd(12) + row[2]);
}
