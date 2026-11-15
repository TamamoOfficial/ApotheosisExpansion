// How many of each Mek baby mob must spawn for one baby boss to appear.
//
// Same chain as the wither calculation:
//   P(boss | this mob spawns) = (babyWeight / totalEligibleWeight) x invaderChance(dimension, tier)
//
// Important: the comparison pool depends on the *biome*, because four upstream invaders carry
// biome constraints (#c:is_cold/overworld, #c:is_dry/overworld, #c:is_wet/overworld, #c:is_snowy).
// So each baby is evaluated in a representative biome and also in a "worst case" biome.
const fs = require('fs');
const path = require('path');
const { rel, apothRefDir } = require('./lib_paths');

const REF = apothRefDir();
const MINE = rel('src', 'main', 'resources', 'data', 'apotheosis', 'apothic_invaders', 'mekanism');

const items = [];
function walk(dir, tag) {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    if (fs.statSync(p).isDirectory()) { walk(p, tag); continue; }
    const j = JSON.parse(fs.readFileSync(p, 'utf8'));
    const c = (j.basic_data && j.basic_data.constraints) || {};
    items.push({
      id: tag + '/' + path.basename(p, '.json'),
      entity: j.entity,
      dims: c.dimensions,
      biomes: c.biomes,
      weights: j.basic_data.weights,
    });
  }
}
walk(REF, 'apoth');
walk(MINE, 'mek');

const RATE = {
  'minecraft:overworld': { frontier: 0.018, ascent: 0.020, summit: 0.025, pinnacle: 0.030 },
  'minecraft:the_end': { frontier: 0.018, ascent: 0.020, summit: 0.025, pinnacle: 0.030 },
  'minecraft:the_nether': { frontier: 0.025, ascent: 0.027, summit: 0.030, pinnacle: 0.035 },
};

function weightFor(it, tier) {
  const t = it.weights;
  if (t.weight !== undefined) return t.weight;
  const e = t[tier];
  return e ? e.weight : 0;
}

// biome -> the conventional tags it carries (only the ones upstream actually uses)
const BIOME_TAGS = {
  'minecraft:plains': [],
  'minecraft:forest': [],
  'minecraft:desert': ['#c:is_dry/overworld'],
  'minecraft:badlands': ['#c:is_dry/overworld'],
  'minecraft:snowy_plains': ['#c:is_snowy', '#c:is_cold/overworld'],
  'minecraft:snowy_taiga': ['#c:is_snowy', '#c:is_cold/overworld'],
  'minecraft:swamp': ['#c:is_wet/overworld'],
  'minecraft:mangrove_swamp': ['#c:is_wet/overworld'],
  'minecraft:the_end': [],
  'minecraft:nether_wastes': [],
};

function eligible(it, ctx) {
  if (it.dims && it.dims.length && !it.dims.includes(ctx.dim)) return false;
  const b = it.biomes;
  if (b === undefined) return true;
  if (typeof b === 'string') {
    if (b.startsWith('#')) return ctx.tags.includes(b);
    return b === ctx.biome;
  }
  if (Array.isArray(b)) return b.includes(ctx.biome);
  return false;
}

function poolFor(ctx, tier) {
  const pool = [];
  for (const it of items) {
    if (!eligible(it, ctx)) continue;
    const w = weightFor(it, tier);
    if (w > 0) pool.push({ entity: it.entity, w });
  }
  return pool;
}

// baby entity -> representative contexts to evaluate
const CASES = [
  { entity: 'mekanismadditions:baby_skeleton', label: '骷髅宝宝', dim: 'minecraft:overworld', biomes: ['minecraft:plains', 'minecraft:forest', 'minecraft:desert', 'minecraft:snowy_plains'] },
  { entity: 'mekanismadditions:baby_stray', label: '流髑宝宝', dim: 'minecraft:overworld', biomes: ['minecraft:snowy_plains', 'minecraft:snowy_taiga', 'minecraft:plains'] },
  { entity: 'mekanismadditions:baby_bogged', label: '沼骸宝宝', dim: 'minecraft:overworld', biomes: ['minecraft:swamp', 'minecraft:mangrove_swamp', 'minecraft:plains'] },
  { entity: 'mekanismadditions:baby_enderman', label: '末影人宝宝', dim: 'minecraft:overworld', biomes: ['minecraft:plains', 'minecraft:desert', 'minecraft:snowy_plains'] },
  { entity: 'mekanismadditions:baby_enderman', label: '末影人宝宝(末地)', dim: 'minecraft:the_end', biomes: ['minecraft:the_end'] },
  { entity: 'mekanismadditions:baby_wither_skeleton', label: '凋灵骷髅宝宝', dim: 'minecraft:the_nether', biomes: ['minecraft:nether_wastes'] },
];

for (const c of CASES) {
  console.log('\n' + '='.repeat(78));
  console.log(c.label + '   [' + c.dim.replace('minecraft:', '') + ']');
  console.log('='.repeat(78));

  for (const biome of c.biomes) {
    const ctx = { dim: c.dim, biome, tags: BIOME_TAGS[biome] || [] };
    const rows = [];
    for (const tier of ['frontier', 'ascent', 'summit', 'pinnacle']) {
      const pool = poolFor(ctx, tier);
      const total = pool.reduce((a, b) => a + b.w, 0);
      const mineW = pool.filter(p => p.entity === c.entity).reduce((a, b) => a + b.w, 0);
      if (mineW === 0) { rows.push({ tier, n: null }); continue; }
      const p = (mineW / total) * RATE[c.dim][tier];
      rows.push({ tier, n: Math.round(1 / p), share: mineW / total, total, mineW });
    }
    const shown = rows.map(r => r.n === null ? r.tier + ':不出现' : r.tier + ':1/' + r.n).join('   ');
    console.log('  ' + biome.replace('minecraft:', '').padEnd(18) + shown);
  }
}

// ---- tuning table: what weight gives what, for the most common case ----
console.log('\n' + '='.repeat(78));
console.log('调参参考：调整该条目权重后的「每 N 只出 1 只 boss」');
console.log('（以上表第一个群系为准，其它条目权重不变）');
console.log('='.repeat(78));

for (const c of CASES.filter(x => !x.label.includes('末地'))) {
  const biome = c.biomes[0];
  const ctx = { dim: c.dim, biome, tags: BIOME_TAGS[biome] || [] };
  console.log('\n-- ' + c.label + '  (' + biome.replace('minecraft:', '') + ')');
  console.log('  权重   ascent      summit      pinnacle');
  const cur = items.find(i => i.entity === c.entity && i.dims && i.dims.includes(c.dim));
  for (const w of [10, 17, 25, 33, 40, 50, 75, 100]) {
    const out = [];
    for (const tier of ['ascent', 'summit', 'pinnacle']) {
      const pool = poolFor(ctx, tier);
      const total = pool.reduce((a, b) => a + (b.entity === c.entity ? 0 : b.w), 0);
      const p = (w / (total + w)) * RATE[c.dim][tier];
      out.push('1/' + String(Math.round(1 / p)).padEnd(9));
    }
    const mark = cur && cur.weights[tier0(cur)] && cur.weights[tier0(cur)].weight === w ? '  <- 当前' : '';
    console.log('  ' + String(w).padEnd(6) + out.join(' ') + mark);
  }
}

function tier0(it) {
  const t = it.weights;
  if (t.weight !== undefined) return 'ascent';
  for (const k of ['frontier', 'ascent', 'summit', 'pinnacle']) if (t[k]) return k;
  return 'ascent';
}
