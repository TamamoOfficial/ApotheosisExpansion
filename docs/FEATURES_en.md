# Feature Details

[中文](FEATURES.md) | **English**

[← Back to README](../README_en.md)

This document explains the **implementation** and **design trade-offs** behind each module.
For a quick overview of what the mod does, see the [README](../README_en.md).

---

## Contents

- [1. Crafted gear rolls affixes](#1-crafted-gear-rolls-affixes)
- [2. Spawn egg unlock](#2-spawn-egg-unlock)
- [3. Fix: anvil repair-combine re-rolls affixes](#3-fix-anvil-repair-combine-re-rolls-affixes)
- [4. Gem drop tuning](#4-gem-drop-tuning)
- [5. Thermal integration](#5-thermal-integration)
- [6. Mekanism Additions integration](#6-mekanism-additions-integration)
- [7. Ars Nouveau integration](#7-ars-nouveau-integration)
- [8. AllTheModium integration](#8-allthemodium-integration)
- [9. Vanilla bosses](#9-vanilla-bosses)
- [10. Fix: bosses no longer target creative/spectator players](#10-fix-bosses-no-longer-target-creativespectator-players)
- [11. Interaction with Apotheosis boss spawners](#11-interaction-with-apotheosis-boss-spawners)

---

## 1. Crafted gear rolls affixes

Equipment the player **crafts by hand** automatically receives Apotheosis affixes and gem sockets.

### Triggers when

- the item came from player crafting (crafting table, smithing table, ...)
- the item is equipment (something that can carry affixes)
- the feature is enabled in config

### Rarity range

`min_rarity` / `max_rarity` bound the roll, defaulting to `apotheosis:common` … `apotheosis:rare`.
Set `respect_world_tier = true` to additionally clamp the upper bound to the player's current world tier.

### Skip rule

With `skip_if_already_affixed = true` (the default) items that already carry affixes are left alone.

### Why the hook is `AbstractContainerMenu.clicked`

`ItemCraftedEvent` fires unreliably for some crafting paths — shift-crafting and recipe-book autofill
in particular. `clicked` is the single choke point for "player took something out of a container",
so it catches every case.

---

## 2. Spawn egg unlock

Vanilla spawners hard-reject the **Warden** and **Elder Guardian** spawn eggs.
`SpawnerUnlockHandler` removes that restriction. The two unlocks are separate switches.

---

## 3. Fix: anvil repair-combine re-rolls affixes

### Root cause

Apotheosis' `RepairItemRecipe` decides whether an item "has affixes" with a fuzzy check. When two
identically-affixed items are combined, the resulting item **re-rolls its affixes**, wiping the rolls
the player worked for.

### Fix

Snapshot both input items at `HEAD` of `clicked`, then copy their affixes onto the result at `TAIL`
so `assemble` no longer re-rolls.

---

## 4. Gem drop tuning

Migrated from the Gem Tweaks mod. Scales, by multiplier:

- each gem's **drop weight** (`gem_weight_multipliers`)
- each drop source's **trigger chance** (`gem_drop_chance_multiplier`)

`gem_weight_multipliers` is a list of `"id=multiplier"` strings. On first launch it is seeded with
every loaded gem id at 1.0, and newly added gems are appended automatically.

**When it applies**: scheduled both on data-pack sync and on config reload;
`GemWeightTweaker` / `GemChanceTweaker` patch Apotheosis' `Gem.weights` and `GemLootModifier.entries`
by reflection.

---

## 5. Thermal integration

Adds Apotheosis Invaders for the Thermal elementals.

### Apotheosis has no such integration

Searching the whole codebase for `thermal` / `cofh` / `basalz` / `blizz` / `blitz` returns **nothing**.

### What is added

| File | Entity | Biome |
|---|---|---|
| `thermal/basalz_peaks.json` | Basalz | Stony Peaks |
| `thermal/basalz_caves.json` | Basalz | Lush Caves |
| `thermal/basalz_nether.json` | Basalz | Basalt Deltas |
| `thermal/blizz.json` | Blizz | Snowy biomes |
| `thermal/blitz_badlands.json` | Blitz | Badlands |
| `thermal/blitz_savanna.json` | Blitz | Savanna |
| `thermal/blitz_sandy.json` | Blitz | Sandy biomes |

**Numbers**: weights 40–60, rarity bounded by each biome's world-tier curve.

Badlands is intentionally covered **twice** (`blitz_badlands` weight 50 + `blitz_sandy` weight 40):
Badlands is both a badlands and a sandy biome, so both entries match and Blitz shows up more often there.

---

## 6. Mekanism Additions integration

Adds Apotheosis Invaders for the Mekanism Additions baby mobs.

### Where the entities live

After Mekanism v10 (1.15+) the entity content moved into the official addon `mekanismadditions`.
The baby mobs live **there**, not in the main Mekanism jar — so this integration depends on
`mekanismadditions`, not `mekanism`.

### Weights

One third of the matching adult invader; the baby Wither Skeleton uses one quarter.

| Baby | Weight | Biome |
|---|---|---|
| Baby Enderman | 17 | Overworld / The End |
| Baby Skeleton | 33 | Overworld |
| Baby Stray | 40 | Overworld / cold biomes |
| Baby Bogged | 40 | Overworld / wet biomes |
| Baby Wither Skeleton | 25 | Nether Fortress |

### The one omission: Baby Creeper

Apotheosis has **no creeper invader** to scale from, so there is no baseline weight and the entry is
skipped.

### Baby Wither Skeleton: how fortress spawning actually works

An easy thing to get wrong: Wither Skeletons spawned by a Nether Fortress report
`MobSpawnType.NATURAL`, **not** `STRUCTURE`.

A structure's `spawn_overrides` only replaces the biome spawn table; at runtime it still goes through
`NaturalSpawner` → `finalizeSpawn(NATURAL)`. That means Apotheosis' invader gate **does** see them,
and a plain data file works.

---

## 7. Ars Nouveau integration

Adds Apotheosis Invaders for the three Wilden mobs.

### How these mobs actually spawn

Not through `biome_modifier` — they are listed in the **`spawn_overrides` of the three Wilden Den
structures**. Same mechanism as Nether Fortresses: entering the structure replaces the biome spawn
table, and spawning runs as `NATURAL`.

### What is added

| File | Entity | Constraint |
|---|---|---|
| `ars_nouveau/wilden_hunter.json` | Wilden Hunter | `ars_nouveau:archwood_biome` |
| `ars_nouveau/wilden_stalker.json` | Wilden Stalker | `ars_nouveau:archwood_biome` |
| `ars_nouveau/wilden_guardian.json` | Wilden Guardian | `#c:is_tree/coniferous` |

### One documented deviation: `haven` is unusable

The plan was to cover `haven ~ ascent`, but Apotheosis' dimension data sets the **`haven` invader
chance to 0.0** — listing it would mean never firing. The entries therefore use
`frontier` + `ascent` only.

---

## 8. AllTheModium integration

Adds an Apotheosis Invader for the Piglich.

### How it spawns

The Piglich only spawns inside the **Ancient Pyramid** structure, via `spawn_overrides`:

```json
"spawn_overrides": {
  "monster": {
    "spawns": [{ "type": "allthemodium:piglich", "weight": 4500, "minCount": 1, "maxCount": 1 }]
  }
}
```

The pyramid only generates in `allthemodium:soul_sand_valley`, inside the `allthemodium:the_other`
dimension. Same mechanism as fortresses, spawning as `NATURAL`, so **a plain data file is enough**.

### Random gear: why an event hook is needed

AllTheModium **1.20.x** gave the Piglich a 50% chance per armour piece:

```java
protected void populateDefaultEquipementSlots(DifficultyInstance diff) {
    if (this.isAdult()) {
        this.maybeWearArmor(EquipmentSlot.HEAD,  new ItemStack(ALLTHEMODIUM_HELMET));
        ...
    }
}
private void maybeWearArmor(EquipmentSlot slot, ItemStack stack) {
    if (this.level.random.nextFloat() < 0.5F) { this.setItemSlot(slot, stack); }
}
```

**The 1.21.1 rewrite removed that along with the baby variant** — `finalizeSpawn` is now an empty
override, so the mob arrives naked. Apotheosis' `GearSet.apply` cannot express "maybe nothing" either,
since it draws one winner per slot unconditionally.

`PiglichGearHook` restores the behaviour on `FinalizeSpawnEvent`:

1. **Independent 50% roll per slot** for the four ATM armour pieces
2. **Main hand: 40% ATM sword / 60% netherite sword** (mirrors 1.20.x `createSpawnWeapon`)
3. **Gear is applied before `initBoss` runs** — the order matters, or the affixes land on nothing

Only the `allthemodium` tier is used, never vibranium or unobtainium.

### Numbers

The Piglich is already extreme (9999 health, 24 armour, 24 toughness, 12 attack):

| Rarity | Health | Attack multiplier | Armour |
|---|---|---|---|
| epic | +160 – 300 | ×1.6 – 2.3 | +8 |
| mythic | +275 – 450 | ×2.75 – 3.63 | +25 |

Knockback resistance is clamped to the 1.0 cap.

---

## 9. Vanilla bosses

Turns the **Warden, Wither and Ender Dragon** into Apotheosis bosses.

### Why event hooks and not data files

Apotheosis only picks invaders inside `FinalizeSpawnEvent`, and only for
`NATURAL`/`CHUNK_GENERATION`. None of the three qualifies:

| Entity | How it enters the world | Spawn type |
|---|---|---|
| Warden | `SculkShriekerBlockEntity` → `SpawnUtil.trySpawnMob` | **`TRIGGERED`** |
| Wither | `WitherSkullBlock` then `addFreshEntity` | never calls `finalizeSpawn` |
| Ender Dragon | `EndDragonFight.createNewDragon` then `addFreshEntity` | never calls `finalizeSpawn` |

The data files are therefore **stat carriers only**: code looks them up by id and calls
`Invader#initBoss` on the already-created entity.

### The three bosses

**Warden** (`FinalizeSpawnEvent`, `TRIGGERED`)

- Rolled on every successful shrieker summon, **10%**
- Tier **Ascent – Pinnacle**
- Rarities `epic` / `mythic` only

**Wither** (`EntityJoinLevelEvent`)

- Rolled when a player builds one, **10%**
- **Overworld**: Ascent – Summit
- **Nether**: Ascent – Pinnacle
- **Any other dimension**: stays vanilla

**Ender Dragon** (`EntityJoinLevelEvent`)

- **Only the first dragon of the save**, always the highest available rarity
- Ancient Reforging installed → `ancientreforging:ancient`, otherwise `apotheosis:mythic`

### How "first dragon" is decided

The vanilla kill-dragon advancement **cannot distinguish the first kill** (it only matches
`player_killed_entity` plus the entity type, so the second and third also fire).
The real flag is `EndDragonFight.previouslyKilled`:

```java
if (fight.hasPreviouslyKilledDragon()) {
    return;   // not the first — leave it vanilla
}
```

It is **read, never written**, so other mods' dragon-egg logic is untouched — vanilla still decides
whether the egg drops.

### Choosing the Ancient rarity at runtime

`ancientreforging` is optional, so `ancientreforging:ancient` may not exist. Hence two data files:

| File | Rarity | Condition |
|---|---|---|
| `vanilla_bosses/ender_dragon.json` | `apotheosis:mythic` | unconditional (fallback) |
| `vanilla_bosses/ender_dragon_ancient.json` | `ancientreforging:ancient` | `mod_loaded(ancientreforging)` |

At runtime the code queries `RarityRegistry.INSTANCE.getValue()`: if the ancient rarity is present it
is used, otherwise the mythic entry.

**Why this cannot be a data condition**: conditions and rarities are resolved in the **same registry
pass**, so a condition cannot observe the final rarity set.

### Dragons and equipment

`EnderDragon` extends `Mob` only (not `Monster`), but it **overrides no equipment methods**, so gear
lands in `armorItems` normally and both attributes and affixes **do apply**. Three known traits:

- invisible on the model (the dragon uses custom rendering with no armour layer)
- not dropped on death (the dragon has its own death handling)
- armour value genuinely applies (`getArmorValue()` → `Attributes.ARMOR`)

---

## 10. Fix: bosses no longer target creative/spectator players

### Symptom

Apotheosis invader bosses actively target creative and spectator players; vanilla mobs never do.

### Root cause: vanilla filtering is bypassed by `setTarget`

Vanilla AI is correct: `NearestAttackableTargetGoal` filters with
`TargetingConditions.forCombat()`, and `Player.canBeSeenAsEnemy()` returns
`!getAbilities().invulnerable && …`. Creative and spectator are both `invulnerable`, so vanilla never
picks them.

Apotheosis instead calls `Mob.setTarget(player)` directly at spawn time, bypassing that filter. There
are two call sites and both are incomplete:

**Gap 1 — natural spawn**

```java
if (rules.autoAggro().orElse(...) && !player.isCreative()) {   // blocks creative, not spectator
    boss.setTarget(player);
}
```

**Gap 2 — boss spawner block**

```java
level.getEntities(PLAYER, aabb, EntitySelector.NO_CREATIVE_OR_SPECTATOR)...  // filtered here
...
entity.setTarget(player);   // forty ticks later, assigned unconditionally
```

### Fix

Both `setTarget` calls are intercepted with `@Redirect` and skipped using **the same predicate vanilla
uses**:

```java
if (target instanceof Player player && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) {
    return;   // let vanilla AI keep ignoring them
}
```

A no-op for survival players — difficulty and behaviour are unchanged.

### On `remap = false`

This project is compiled with plain `javac` and ships **no refmap**. The runtime is
`Service=ModLauncher`, and NeoForge 1.21.1 production uses official names, so
`net.minecraft.world.entity.Mob;setTarget(...)` matches directly; `remap = false` states "do not
remap" explicitly and avoids a missing-refmap warning.

---

## 11. Interaction with Apotheosis boss spawners

### How Apotheosis spawners work

A boss spawner block **randomly draws** an invader from the registry. This mod's integration entries
live in that same registry, so they must be excluded — otherwise a spawner could produce, say, a
Basalz boss, which is not the intent.

### How this mod avoids polluting that pool

`BossSpawnerTileMixin` redirects the spawner's draw and `InvaderSpawnerFilter` decides whether an
entry belongs to this mod:

```java
private static final Set<String> OWN_FOLDERS = Set.of(
    "thermal", "mekanism", "ars_nouveau", "allthemodium", "vanilla_bosses");
```

On a hit it **re-rolls** (up to 8 attempts); if every attempt lands on our entries the original result
is kept, which beats spawning nothing (the block has already been destroyed by
`setBlockAndUpdate(AIR)`).

Only the spawner block is affected. Natural spawning, commands, the summoner item and gateways are
untouched.

### Data folder naming

The five data folders use plain mod names, matching Apotheosis' own
(`overworld` / `the_nether` / `the_end` / `twilight`):

```
data/apotheosis/apothic_invaders/
├── thermal/          Basalz / Blizz / Blitz
├── mekanism/         five baby mobs
├── ars_nouveau/      Wilden Hunter / Stalker / Guardian
├── allthemodium/     Piglich
└── vanilla_bosses/   Warden / Wither / Ender Dragon
```

**Adding a new integration folder means adding it to `OWN_FOLDERS`**, or that group will leak into
spawners. `build/test_filter_predicate.js` checks both directions: every folder on disk must be in the
set, and every set member must exist on disk.
