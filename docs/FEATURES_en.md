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
- [12. Shared monster budget for grouped players](#12-shared-monster-budget-for-grouped-players)
- [13. Spawner rune tooltips](#13-spawner-rune-tooltips)
- [14. Gem compendium (JEI/EMI)](#14-gem-compendium-jeiemi)

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

Vanilla `RepairItemRecipe#assemble` builds a brand-new `ItemStack(item)` and carries over only durability
and curse enchantments. Every Apotheosis component — affixes, rarity, sockets — is dropped, so
`skip_if_already_affixed` sees a clean new item and the output **re-rolls its affixes**. Merging two
fully-affixed pickaxes wipes the rolls the player worked for.

### Fix

Snapshot the crafting grid at `HEAD` of `clicked`, ask the recipe manager which recipe **actually
matched**, and branch on that at `TAIL`:

| Matched recipe | Treatment |
|---|---|
| `RepairItemRecipe` (vanilla repair combine) | **Never re-rolled.** Affixed inputs → inherit the best one; plain inputs → output stays plain |
| Any other recipe (shaped / shapeless / modded / other custom) | Rolls normally, still bounded by `skip_if_already_affixed` |

### Why the recipe is identified, not guessed

The first implementation only asked "is the output the same item as an input?". That also matches
ordinary shaped and shapeless recipes, while the repair recipe is the one recipe that **consumes both
inputs and rebuilds a plain stack**. Guessing meant affixes could leak from a consumed ingredient onto
a genuinely new craft.

### Why a combine output must never be rolled (the reported exploit)

Treating a merge output as a "newly crafted item" means **two cheap plain damaged tools mint one
affixed item on every merge**. That is both a free affix source and a way around everything
`skip_if_already_affixed` exists to prevent. Therefore:

- "a merge never re-rolls" is **not configurable** — a bug fix a player can switch off is not a fix;
- `inherit_on_repair_combine` only decides whether the inputs' data is carried over. With it off the
  output is plain, not re-rolled.

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

---

## 12. Shared monster budget for grouped players

### How the vanilla cap works

The spawn budget is enforced in **two** places at once, and relaxing only one of them changes nothing:

| Scope | Location | Rule |
|---|---|---|
| Per player | `LocalMobCapCalculator.canSpawn` | Each player within 128 blocks gets 70; spawning is allowed when **any** of them has room |
| Per level | `NaturalSpawner.SpawnState.canSpawnForCategory` | `70 × (union of every player's 17×17 spawn boxes) / 289` |

The second one is the trap: it uses the **union**. Two players standing together have almost entirely
overlapping boxes, so the union stays near 289 — and the cap stays at **one player's worth (70)**. That
is why two players idling on one platform gain nothing.

### What this mod changes

Both limits are multiplied by the same factor:

```
multiplier = 1 + bonus × (n - 1)          n = nearby players sharing a world tier
```

With the default `bonus = 0.5`:

| Same-tier players | Multiplier | Monster cap |
|---|---|---|
| 1 | ×1.0 | 70 (vanilla) |
| 2 | ×1.5 | **105** |
| 3 | ×2.0 | **140** |
| 4 | ×2.5 | 175 |

To reach 140 with only two players, set `mob_cap_bonus_per_player` to `1.0`.

### Why only same-tier players count

Apotheosis already picks mob strength from the **nearest** player's world tier (invader chance, tier
augments, elite weights). Counting all players would let a haven player standing next to a pinnacle
player raise the budget too, which contradicts that model. Restricting to one tier keeps both systems
consistent: **same-tier groups are rewarded, mixed-tier groups are not.** If several tiers are nearby,
the **largest single group** is used.

### Hostile mobs only

`MobCapScaler.scaleCap` returns the base value unchanged for `category.isFriendly()`. Animals and water
creatures keep their own vanilla caps — widening those would only let passive farms eat into the hostile
budget through unrelated bookkeeping.

### Spawners are untouched

`BaseSpawner` uses its own `MaxNearbyEntities` (default 6) and never passes through the
`NaturalSpawner` cap check. This change does not touch it either.

### Implementation

| Class | Role |
|---|---|
| `spawn/MobCapScaler` | Multiplier math, same-tier grouping, per-(level, tick, chunk) memo |
| `mixin/spawn/LocalMobCapCalculatorMixin` | Pushes the chunk's multiplier at the head of `canSpawn` |
| `mixin/spawn/MobCountsMixin` | Redirects `getMaxInstancesPerChunk()` — the per-player 70 |
| `mixin/spawn/SpawnStateMixin` | Redirects the same call — the level-wide 70 |

The multiplier travels through a `ThreadLocal<Deque<Double>>`: `MobCounts` is a package-private nested
class that only receives a `MobCategory` and has no access to the chunk, so the outer method computes it
and the nested one reads it back. A deque rather than a single value keeps the outer frame intact if a
spawn callback re-enters spawning.

The multiplier **stays fractional**. Rounding it first would turn a 0.5 bonus at two players into ×2
(140) instead of the documented ×1.5 (105).

### HUD mods and the displayed cap

MiniHUD and Servux **do not read** the result of the spawn check. Each recomputes the same formula:

```java
int capacity = category.getMaxInstancesPerChunk() * spawnableChunks / 289;
```

So after the cap is widened they keep printing `x/70` — the panel disagrees with the world.

`sync_mob_cap_to_hud` (**cold switch, off by default**) redirects that single
`getMaxInstancesPerChunk()` call inside their code, via the separate
`apotheosis_expansion.compat.mixins.json`. Their own arithmetic (multiply by chunk count,
divide by 289) is left intact:

| Target | Class → method | Role |
|---|---|---|
| MiniHUD | `MobCapDataHandler` → `lambda$updateIntegratedServerMobCaps$1` | Single-player / integrated-server local calculation |
| Servux | `DataLoggerMobCaps` → `getResult` | Server push, which MiniHUD prefers over its own numbers |

**With the switch off there is no side effect at all**: the bridge returns `null` and each handler
falls back to `receiver.getMaxInstancesPerChunk()`, i.e. the unpatched value.

**Why a separate config plus a plugin**: these two mixins target classes that live in other mods.
When the target mod is absent, Mixin cannot resolve the class. `CompatMixinPlugin` consults
`ModList` in `shouldApplyMixin`, so an uninstalled mod means the mixin is **never attempted** and
no error is produced.

**Display granularity**: the per-dimension maximum — the highest multiplier any player in that
dimension can reach — matching the HUD's own level-wide granularity, not the value for the block
under your feet. Friendly categories are not scaled, exactly as on the spawn side.

### Fake players and HUD compat: three traps that only show up at runtime

All three compile cleanly and start cleanly, and their symptoms look alike. Recorded here so they are not repeated.

#### 1. A mixin package must not contain ordinary classes

Mixin treats the config's `package` as a package **it owns**. Every class inside is parsed as a mixin, and
**direct references to it are forbidden**, producing at runtime:

```
IllegalClassLoadError: ... is in a defined mixin package ... and cannot be referenced directly
```

The crash lands in `ServerChunkCache.tickChunks` - only when spawning happens - so **the title screen is
perfectly fine**. "The game launches" therefore proves nothing about whether the injection works.

`LocalMobCapAccess`, `MobCapHudBridge` and `CompatMixinPlugin` therefore live under `spawn/` and `compat/`;
`mixin/` holds nothing but `@Mixin` classes. Section 1 of `build/verify_hud_compat.js` enforces this.

#### 2. Never probe for a target class with `Class.forName`

`CompatMixinPlugin` has to tell whether a third-party mod is present. The first version only asked
`ModList.isLoaded`, which is not yet populated when a Mixin plugin runs, so **all three targets returned false**
and every compat mixin silently stopped applying.

The second version added `Class.forName(className, false, loader)` as a fallback - **a worse mistake**: it
**defines and loads** the class, and once a class is in the loader's cache Mixin can never transform it again.
The plugin then correctly reported `enabled` while `Mixing ... into ...` never happened, and because the config
is `"required": false`, **nothing was logged at all**.

The correct check reads the resource and defines nothing:

```java
String resource = className.replace('.', '/') + ".class";
return loader.getResource(resource) != null;
```

`verify_hud_compat.js` strips comments and searches for `Class.forName` to keep this from coming back.

#### 3. A fake player's world tier defaults to `HAVEN`

A fake is a real `ServerPlayer` that never logs in, so its `WorldTier` attachment keeps the default `HAVEN`.
The shared cap only counts players of the **same tier**, so a fake standing next to you changes nothing.

With `fake_players_inherit_world_tier` on, tiers are inherited in this order:

| Priority | Source | May overwrite an existing tier |
|---|---|---|
| 0 | The **issuer** of `/player ... spawn` (captured at command HEAD by `CarpetPlayerCommandMixin`; command blocks included) | yes |
| 1 | An online real player with the same name | yes |
| 2 | An online real player with the same UUID (carpet shadow players) | yes |
| 3 | The nearest real player in the same dimension | no - fills in the default only |

Source 3 is a pure guess, used only to fill an unset tier; otherwise a fake parked on a shared platform would
change owner whenever someone walked past.

**Note**: the tier is written to `playerdata`, so it updates on the next spawn - existing fakes do not change.

### Diagnostics

| Switch | Log tag | Purpose |
|---|---|---|
| `debug_mob_cap` | `ApotheosisExpansion/MobCap` | Per chunk per tick: nearby player tiers, group size, multiplier |
| always on | `ApotheosisExpansion/FakeTier` | Tier inheritance: from whom, via which source, result |
| `debug_mob_cap` | `ApotheosisExpansion/HudHook` | Proof of life for MiniHUD's cap read (present = injection installed and called) |

The `HudHook` line matters most: if it never appears, the injection did not apply or the HUD never runs that
calculation - at which point "the display logic must be wrong" is no longer a valid hypothesis.

### The cross-tier counting switch

By default only **same-tier** players count, matching Apotheosis' own tier model. With
`mob_cap_count_all_tiers` on, every non-spectator player in range counts instead - the literal
`70 × (1 + 0.5 × (nearby - 1))`:

| Case | Off (default) | On |
|---|---|---|
| 1 haven + 2 frontier | counts the 2 frontier players → x1.5 (105) | counts all 3 → x2.0 (140) |

Both modes share the same formula and the `mob_cap_max_players` ceiling, and both ignore spectators and
friendly categories.

### Hot reload: an in-game edit applies immediately

None of the cap options require a restart, a relog, or `/reload`.

> **Note**: `/reload` reloads **data packs** (`MinecraftServer.reloadResources` rebuilds
> `ReloadableServerResources`). It does **not** reload a mod's TOML config.

Three paths all work:

1. **In-game config screen** - change the value, hit Done/Save, it applies at once
2. **Edit the toml file and save it** - NeoForge's `ConfigTracker` installs a file watcher and fires
   `ModConfigEvent.Reloading`
3. No command is needed for either

This works because of three deliberate choices:

| Choice | Why |
|---|---|
| Options are **not** marked `worldRestart`/`gameRestart` | otherwise NeoForge keeps the old cached value until a restart |
| Every use reads `Config.xxx()`, nothing is copied into a static field | otherwise the value freezes at class-init time |
| Every config change calls `MobCapScaler.clearCache()`, and the memo key carries a **generation counter** | see below |

**The generation counter** is the subtle part. Clearing the map is not sufficient: a chunk can be queried
twice within one tick, so a value written before the clear would still look structurally valid and keep
being served. Folding `GENERATION++` into the key makes the old entry **impossible to match** instead of
relying on clear-then-read ordering.

`build/test_hot_reload.js` guards all of this: no restart markers, no static caching, and a memo key that
carries a generation counter which `clearCache` increments.

**Fake player tiers are the one exception**: `fake_players_inherit_world_tier` is evaluated when a fake
**joins**, so flipping the switch needs a **re-spawn** of that fake - not a caching problem, the event
simply only happens once.

---

## 13. Spawner rune tooltips

Current Apotheosis replaced "feed a pile of items to the spawner" with **runes**: each upgrade rune
(Frontier / Ascent / Summit / Pinnacle) is really a recipe that *sets* five spawner stats to fixed values.
The item itself only says one line - "Slightly improves a Mob Spawner" - and the numbers live in the
JEI/EMI **uses** page, where they only appear after opening that page and hovering each line.

### Where the numbers come from

**Nothing is hard-coded; the recipes are read.** Each rune is one `apothic_spawners:spawner_modifier` recipe:

```json
{
  "type": "apothic_spawners:spawner_modifier",
  "mainhand": { "item": "apotheosis:frontier_spawner_upgrade_rune" },
  "stat_changes": [
    { "type": "apothic_spawners:min_delay",           "mode": "set", "value": 150 },
    { "type": "apothic_spawners:max_delay",           "mode": "set", "value": 600 },
    { "type": "apothic_spawners:spawn_count",         "mode": "set", "value": 7   },
    { "type": "apothic_spawners:max_nearby_entities", "mode": "set", "value": 12  },
    { "type": "apothic_spawners:req_player_range",    "mode": "set", "value": 24  }
  ]
}
```

The client already receives that recipe table from the server (it is what JEI renders the uses page from), so
the tooltip asks `RecipeManager.getAllRecipesFor(...)` for the recipes whose main hand is this rune and decodes
their `StatModifier` list. **A data pack change therefore changes the display**: a pack that retunes the
numbers, or another mod that ships its own runes, needs no change here.

That is also why the values cannot go stale - every draw reads the currently active recipes.

### How the three kinds of value are shown

| Recipe form | Tooltip |
|---|---|
| `"mode": "set"`, numeric | `Min Spawn Delay: 150` (green) |
| `"mode": "set"`, boolean | `Enables: Ignore Light` / `Disables: Ignore Light` |
| default `add` mode | `+2 Spawn Range (max 32)` |

Stat names and value units (ticks, percentages, roman numerals for levels) are reused from Apothic Spawners'
own `SpawnerStat#name()` and `#formatValue()`, so the translation and the formatting live in exactly one place.

**Bounds are shown too**, because they are part of the real behaviour: the spawn-range rune adds 2 per use and
stops at 32, so printing only "+2" would suggest it stacks forever. The echoing rune's `max: 3` behaves the
same way.

### Only the "main hand alone" form is described

The same rune can have several recipes: the shipped one, plus the `spawner_modifiers/_inverse/` entry that puts
quartz in the off hand and turns the effect back off. Only the recipe with an **empty off hand** is described;
listing both would make the tooltip contradict itself (on and off at once).

### Caching and reloads

A tooltip is re-gathered every frame, so the scan is memoised per **item**. The memo is dropped at two moments:

| Event | Why it is needed |
|---|---|
| `RecipesUpdatedEvent` | the client received a new recipe table (join, `/reload`); the old scan is stale at once |
| `OnDatapackSyncEvent` | a data pack reload finished; on an integrated server the client memo must be dropped too |

The config switch needs **no** invalidation - every draw reads `Config.enableSpawnerRuneTooltips()`, so it is
hot-reloadable.

### Optional dependency isolation

Apothic Spawners is optional. Any class that mentions `dev.shadowsoffire.apothic_spawners` throws
`NoClassDefFoundError` as soon as it is linked, so every such reference is confined to
`spawnerrune/SpawnerRuneTooltips.java`, while `spawnerrune/SpawnerRuneIntegration.java` (which holds none of that
mod's types) asks `ModList` first:

```java
if (FMLEnvironment.dist.isClient() && SpawnerRuneIntegration.isAvailable()) {
    ClientIntegration.registerSpawnerRuneTooltips();
}
```

The client-only `RecipesUpdatedEvent` can therefore only be registered in `ClientIntegration` - registering it
from common code would make a dedicated server resolve a client event class.

Without Apothic Spawners the option does not appear under `[spawner]`, the feature does not exist, and nothing
logs an error.

`build/verify_spawner_rune_tooltips.js` guards these invariants statically: the API still exists, the four
upgrade runes still set all five stats, no Apothic Spawners type appears outside `spawnerrune/`, and the cache
invalidation really is wired to events.

---

## 14. Gem compendium (JEI/EMI)

Apotheosis' Patchouli entry only says gems "may be found in chest loot and rarely from mobs" - **which gem
drops in which dimension, from which world tier it starts dropping, and what each purity's bonus actually
is** live only in data files, with no way to look any of it up in game. This section adds a gem compendium to
JEI and to EMI that lays those three facts out.

### Page contents

**One page per gem** (not one per purity - that would pile dozens of near-identical entries into the category
list):

- **Drops from**: which dimensions the gem can drop in (core gems drop everywhere, the overworld set only in
  the overworld, the end set only in the end...; modded dimensions such as `twilightforest:twilight_forest`
  are shown by id), and which world tiers (tiers with weight 0 are not shown - that tier never rolls the gem);
- **Extra gates**: gems restricted by biome or game stages say so explicitly; a page never silently omits a
  gate the player cannot pass;
- **Bonus table**: one row per bonus, and within the row only the *numbers* at each purity in the game's own order
  (cracked to perfect, only purities at or above the gem's `min_purity`), joined by "/" with the shared wording
  printed once - e.g. "Melee Weapons: +1 / +2 / +3.5 / +5 / +7 / +10 Attack Damage". The compression cuts at digit
  boundaries so "10%" is never split into "1 / 0%". A purity a bonus does not cover prints "-", so the columns
  never shift; a bonus that does not scale with purity prints one value with no slashes. The wording and numbers
  **are exactly what Apotheosis' own tooltip shows** - the page calls `GemBonus#getSocketBonusTooltip` instead of
  re-implementing the formats.

### Where the data comes from

**Nothing is hard-coded; the pages read the loaded gem registry.** They are built when the viewer rebuilds
its index (world join, `/reload`, F3+T) from `GemRegistry`, so: a pack that retunes weights or constraints,
another mod that ships its own gems, or this mod's own `gem_weight_multipliers` zeroing a gem out - the
compendium always shows **the rules that are actually in effect right now**.

### With both JEI and EMI installed: EMI only

EMI ships the JEMI bridge, which imports JEI plugin pages **verbatim** into its own index. Registering both
halves would list every gem page twice. So the arbitration rule is:

| Installed | Result |
| --- | --- |
| JEI only | JEI pages |
| EMI only | EMI pages |
| **both** | **EMI pages only; the JEI plugin stays completely silent** |

The decision lives in `GemCompendiumGate`: a class-resource probe first (never `Class.forName`, the same
approach every other integration in this mod uses), with `ModList` as a second opinion. The JEI plugin class
is still instantiated by JEI - that is how annotation discovery works - but every registration method asks
`useJei()` first and returns without registering anything when both viewers are present.

### Reaching a page

- JEI: the gem item itself is the page's input and (invisible) output slot, so U/R on any gem lands on its
  pages; the layout reuses JEI's own information page (170x125, slot row on top, scrolling text box below);
- EMI: the same output link (a page's output slot is its gem), long pages paginate automatically, and the
  prev/next buttons are the ones EMI's own information pages use.

`gems.enable_gem_compendium` (default on) switches the whole feature off; the key only appears in the config
file on a client that has JEI or EMI installed - a dedicated server, or a client with neither viewer, never
registers it. Changes take effect after F3+T or rejoining a world.

`build/verify_gem_compendium.js` guards these invariants statically: the EMI-wins arbitration covers every
registration entry point, the probe targets really exist in the JEI/EMI jars, the discovery annotations are on
the compiled classes, every translation key the pages use resolves in both language files, and the
dimension/tier/min-purity rules are checked against all 23 gems shipped in the Apotheosis jar.

---

## 15. Adventure: two protections for player-named mobs

The two switches under `[adventure]` solve one class of problem: **a mob the player personally named
with a name tag should not be silently deleted or replaced by a global vanilla rule**. Both share the
same condition - the entity tag `apotheosis_expansion.named`, applied by `NameTagItemMixin` at the
moment a player successfully uses a name tag.

### Why a tag and not `hasCustomName`

`hasCustomName` means far more than "a player named this": Apotheosis stamps a generated name on every
invader and elite, spawn eggs can carry a name component, and slime split children inherit the parent's
name. A name-based rule would protect mobs the player never touched. The scoreboard tag, by contrast, is
vanilla, persists in the save, shows up in `/data get entity`, can be added or removed by `/tag`, and in
this mod the name tag is its only writer.

### 15.1 Named mobs survive Peaceful (`enable_named_peaceful_protection`)

Vanilla `Mob#checkDespawn` tests Peaceful *before* it ever looks at the persistence flag:

```java
if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
    this.discard();              // <- this branch ignores isPersistenceRequired
} else if (!this.isPersistenceRequired() && ...) {
    ...distance culling...       // <- what setPersistenceRequired from a name tag feeds
}
```

So a name tag does nothing against a switch to Peaceful. The fix uses NeoForge's own
`MobDespawnEvent`: `checkDespawn` starts with `EventHooks.checkMobDespawn(this)`, whose contract
explicitly includes "may be used to keep mobs from despawning in peaceful mode"; returning `DENY`
skips the deletion. The handler runs at `LOWEST` priority because this is a Result-style event (every
handler writes the same field and the last writer wins); this mod only ever raises `DENY`, never
`ALLOW`, so being last takes nothing away from anyone.

### 15.2 Named piglins never zombify (`enable_named_piglin_no_zombify`)

Piglin conversion knows nothing about player intent. `AbstractPiglin#isConverting` is just three tests:

```java
return !this.level().dimensionType().piglinSafe()   // neither the Overworld nor the End is safe
    && !this.isImmuneToZombification()              // only the NBT field IsImmuneToZombification ...
    && !this.isNoAi();                              // ... and a nether portal grant immunity
```

`customServerAiStep` counts `timeInOverworld` up while that gate holds and converts past 300 ticks
(15 seconds). Lead a pet piglin through a portal into the Overworld and it becomes a zombified piglin -
gear, tags and AI all replaced - fifteen seconds later.

`NamedPiglinConversionMixin` injects at the head of `isConverting` and returns false for a tagged mob.
The timer never even starts. That is cleaner than cancelling `LivingConversionEvent.Pre`, which fires
every tick from the 300th on, does not stop the counter, and would leave the mob standing in its
pre-conversion retreat behaviour (`PiglinAi` picks the retreat sound through the same gate). Remove the
tag or the toggle and the 15-second count restarts from zero.

Scope is the piglin family only, as requested: `AbstractPiglin` covers piglin and piglin brute.
Hoglin declares its own `isConverting` instead of inheriting this one, so the mixin structurally
cannot reach it. `remap = false` for the usual project-wide reason: the build ships no refmap, so
anchors must be written in production names.

### Hot reload

Both features read the config live (per event / per tick): saving the TOML is enough, no `/reload`,
and the change applies to mobs already in the world immediately.

`build/verify_named_piglin_mixin.js` guards every assumption of the second feature statically: the
production name `isConverting()Z`, that `customServerAiStep` really gates on it at threshold 300,
that Hoglin is not on the inheritance path, the injector shape (cancellable +
`CallbackInfoReturnable<Boolean>`), and the config-key / mixin-registration / lang-key wiring.
