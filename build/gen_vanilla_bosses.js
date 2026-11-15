// Generates invader entries for vanilla bosses: Warden, Wither, Ender Dragon.
//
// These are NOT natural-spawn invaders like the Thermal/Mek/Ars ones. Their spawn paths bypass
// finalizeSpawn entirely, so the entries below are never selected by Apotheosis' own spawn logic;
// they exist purely as the data carrier that the mod's event hooks look up by id and then apply to
// the already-created vanilla boss via Invader#initBoss.
//
// Because of that, "weights" and "constraints" are placeholders that never participate in a random
// draw. They are still written with sane values so the entries decode and so a future read of the
// JSON is not misleading.
//
// Ender Dragon gets TWO entries:
//   ender_dragon.json         -> mythic stats (always valid; mythic ships with Apotheosis)
//   ender_dragon_ancient.json -> ancient stats, gated on mod_loaded(ancientreforging)
// The runtime picks the ancient one when the rarity exists, else falls back to mythic.
const fs = require('fs');
const path = require('path');
const { apothRefDir } = require('./lib_paths');

const REF = apothRefDir();
const OUT = process.argv[2] ||
  'src/main/resources/data/apotheosis/apothic_invaders/vanilla_bosses';

const zombie = JSON.parse(fs.readFileSync(path.join(REF, 'overworld/zombie.json'), 'utf8'));

const GROUP_CONDITION = { type: 'apotheosis_expansion:vanilla_bosses_enabled' };

function entityCondition(entity) {
  return { type: 'apotheosis_expansion:entity_invader_enabled', entity };
}

/**
 * Builds one boss entry.
 * @param opts.entity       entity id the hook looks up
 * @param opts.rarityStats  map of rarity id -> BossStats (copied from the zombie curve unless overridden)
 * @param opts.conditions   extra conditions appended after the group condition
 * @param opts.size         boss AABB
 * @param opts.gear         gear pool key suffix, 'melee' or 'ranged'
 */
function bossEntry(opts) {
  const conditions = [GROUP_CONDITION, entityCondition(opts.entity), ...(opts.conditions || [])];
  const gearSets = {};
  for (const tier of ['haven', 'frontier', 'ascent', 'summit', 'pinnacle']) {
    gearSets[tier] = ['#' + tier + '_' + opts.gear];
  }
  return {
    'neoforge:conditions': conditions,
    type: 'apotheosis:invader',
    basic_data: {
      bonus_loot: ['apotheosis:entity/boss_drops', 'apotheosis:entity/rare_boss_drops'],
      // dimension-only, unused at runtime; these entries are never randomly selected
      constraints: { dimensions: opts.dimensions || ['minecraft:overworld'] },
      name: 'use_name_generation',
      valid_gear_sets: gearSets,
      weights: { quality: 0.1, weight: 1 },
    },
    entity: opts.entity,
    size: opts.size,
    stats: opts.rarityStats,
  };
}

// Stat curves. The vanilla bosses are far above normal mobs, so the zombie curve is scaled up
// rather than reused verbatim: these are meant to be the hardest fights in the world, and the
// hook applies them on top of an already-boss-level base mob.
function percentOf(base, factor) {
  return Math.round(base * factor);
}

/** Deep-copies the zombie stage and scales its numeric values. */
function scaledStage(stage, factor) {
  const s = JSON.parse(JSON.stringify(stage));
  for (const m of s.attribute_modifiers) {
    // scale is a direct multiplier on the model; blowing it up by the HP factor would make the
    // dragon and warden absurdly large, so it is left exactly as the vanilla curve had it.
    if (m.attribute === "minecraft:generic.scale") continue;
    if (typeof m.value === "number") {
      m.value = m.operation === "add_value"
        ? Math.round(m.value * factor)
        : Math.round(m.value * Math.min(factor, 1.5) * 100) / 100;
    } else if (m.value && typeof m.value === "object") {
      const f = m.operation === "add_value" ? factor : Math.min(factor, 1.5);
      m.value.min = Math.round(m.value.min * f * 100) / 100;
      m.value.max = Math.round(m.value.max * f * 100) / 100;
    }
  }
  return s;
}

function stagesFor(rarityIds, factor) {
  const out = {};
  for (const r of rarityIds) {
    const src = zombie.stats['apotheosis:' + r];
    if (!src) throw new Error('zombie has no stage ' + r);
    out['apotheosis:' + r] = scaledStage(src, factor);
  }
  return out;
}

const files = {};

// ---- Warden: natural-ish (TRIGGERED) spawn, mythic-capable ----
// Epic + mythic only: a warden is already a late-game fight.
files['warden.json'] = bossEntry({
  entity: 'minecraft:warden',
  gear: 'melee',
  size: { width: 0.9, height: 2.9 },
  dimensions: ['minecraft:overworld'],
  rarityStats: stagesFor(['epic', 'mythic'], 2.5),
});

// ---- Wither: player-built, tiers by dimension ----
files['wither.json'] = bossEntry({
  entity: 'minecraft:wither',
  gear: 'melee',
  size: { width: 0.9, height: 3.5 },
  dimensions: ['minecraft:overworld', 'minecraft:the_nether'],
  rarityStats: stagesFor(['uncommon', 'rare', 'epic', 'mythic'], 2.0),
});

// ---- Ender Dragon: first-kill only ----
// The dragon has no equipment slots that render, but gear still grants its attributes and affixes,
// which is exactly what was asked for. Mythic is the fallback rarity.
files['ender_dragon.json'] = bossEntry({
  entity: 'minecraft:ender_dragon',
  gear: 'melee',
  size: { width: 16.0, height: 8.0 },
  dimensions: ['minecraft:the_end'],
  rarityStats: {
    'apotheosis:mythic': scaledStage(zombie.stats['apotheosis:mythic'], 6.0),
  },
});

// ---- Ender Dragon, ancient rarity variant (only when AncientReforging is installed) ----
files['ender_dragon_ancient.json'] = bossEntry({
  entity: 'minecraft:ender_dragon',
  gear: 'melee',
  size: { width: 16.0, height: 8.0 },
  dimensions: ['minecraft:the_end'],
  conditions: [{ type: 'neoforge:mod_loaded', modid: 'ancientreforging' }],
  rarityStats: {
    'ancientreforging:ancient': scaledStage(zombie.stats['apotheosis:mythic'], 7.5),
  },
});

fs.mkdirSync(OUT, { recursive: true });
for (const f of fs.readdirSync(OUT)) if (f.endsWith('.json') && !files[f]) fs.unlinkSync(path.join(OUT, f));

for (const [name, obj] of Object.entries(files)) {
  fs.writeFileSync(path.join(OUT, name), JSON.stringify(obj, null, 4) + '\n');
  console.log(name.padEnd(28) + obj.entity.padEnd(30) + 'rarities=' + Object.keys(obj.stats).join(','));
}
console.log('\n' + Object.keys(files).length + ' files -> ' + OUT);
