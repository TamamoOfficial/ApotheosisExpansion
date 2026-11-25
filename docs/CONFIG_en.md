# Configuration & Building

[中文](CONFIG.md) | **English**

[← Back to README](../README_en.md) · [Feature details](FEATURES_en.md)

---

## Config file

Located at `config/apotheosis_expansion-common.toml`: **5 sections, 47 entries**
(31 literal values + 16 per-entity switches, the latter only registered when their mod is present).

---

## Nested menu

The in-game config screen uses **one sub-section per mod**:

```
Apotheosis Expansion — Config
└── Compat
    ├── Thermal                     ← sub-section
    │   ├── enable_thermal_invaders    group switch
    │   ├── enable_basalz_invader      Basalz
    │   ├── enable_blizz_invader       Blizz
    │   └── enable_blitz_invader       Blitz
    ├── Mekanism Additions          ← sub-section
    │   ├── enable_mekanism_invaders   group switch
    │   └── ... 5 per-mob switches
    ├── Ars Nouveau                 ← sub-section
    │   └── ... 3 Wilden switches
    ├── AllTheModium                ← sub-section
    │   └── enable_piglich_boss
    └── Vanilla Bosses              ← sub-section
        ├── enable_vanilla_bosses      group switch
        ├── enable_warden_boss
        ├── enable_wither_boss
        └── enable_ender_dragon_boss
```

### Absent mods are not registered

A sub-section is only registered when its mod is installed. A pack without Mekanism Additions will
**not see** the Mekanism group at all — no dead switches that silently do nothing.

The trade-off: the config file differs between environments. Move to a pack without that mod and that
sub-section's values are gone.

---

## Full key reference

| Section | Key | Meaning |
|---|---|---|
| `crafting` | `enable_affixes` | Whether crafted gear rolls affixes |
| `crafting` | `affix_chance` | Trigger chance (0–1) |
| `crafting` | `enable_sockets` | Whether crafted gear rolls gem sockets |
| `crafting/socket_range` | `min` / `max` | Minimum / maximum sockets |
| `crafting` | `socket_chance` | Trigger chance (0–1) |
| `crafting` | `skip_if_already_affixed` | Skip items that already have affixes |
| `crafting` | `allow_non_equipment` | Also process non-equipment items |
| `crafting` | `inherit_on_repair_combine` | Carry the inputs' affixes over on repair-combine (the merge itself never re-rolls; not configurable) |
| `rarity` | `min_rarity` | Lowest rarity that can be rolled |
| `rarity` | `max_rarity` | Highest rarity that can be rolled |
| `rarity` | `respect_world_tier` | Also clamp the cap to the world tier |
| `spawner` | `enable_warden_unlock` | Allow Warden eggs in spawners |
| `spawner` | `enable_elder_guardian_unlock` | Allow Elder Guardian eggs in spawners |
| `spawner` | `enable_spawner_rune_tooltips` | List what a spawner rune changes in its tooltip (needs Apothic Spawners; client-side) |
| `player` | `enable_respawn_health_fix` | Respawn at the real maximum health |
| `player` | `enable_mob_cap_scaling` | Scale the monster cap by same-tier player count |
| `player` | `mob_cap_count_all_tiers` | Count every nearby player, ignore tier (default off) |
| `player` | `mob_cap_bonus_per_player` | Multiplier per extra player (default 0.5) |
| `player` | `mob_cap_max_players` | Player-count ceiling for the bonus |
| `player` | `sync_mob_cap_to_hud` | Push the scaled cap to MiniHUD / Servux (cold switch) |
| `player` | `fake_players_inherit_world_tier` | Fake players inherit their summoner's world tier |
| `player` | `debug_mob_cap` | Log the cap multiplier calculation (diagnostics) |
| `player` | `enable_spawn_lanes` | Extra spawn pass per chunk at high regional difficulty |
| `player` | `spawn_lanes_min_regional_difficulty` | Regional difficulty needed for the second pass (default 5.5) |
| `player` | `spawn_lanes_real_players_only` | Count only real players for the gate (default on) |
| `adventure` | `enable_named_peaceful_protection` | Mobs tagged `apotheosis_expansion.named` survive Peaceful |
| `adventure` | `enable_named_piglin_no_zombify` | Named piglins/brutes never zombify in the Overworld |
| `gems` | `enable_gem_compendium` | Gem compendium pages in JEI/EMI (the key only exists with a viewer installed) |
| `gems` | `enable_gem_tweaks` | Master switch for gem drop tuning |
| `gems` | `gem_drop_chance_multiplier` | Global chance multiplier |
| `gems` | `gem_weight_multipliers` | List of `"id=multiplier"` |
| `compat/thermal` | `enable_thermal_invaders` | Thermal elementals, group switch |
| `compat/thermal` | `enable_basalz_invader` | Basalz |
| `compat/thermal` | `enable_blizz_invader` | Blizz |
| `compat/thermal` | `enable_blitz_invader` | Blitz |
| `compat/mekanism` | `enable_mekanism_invaders` | Mekanism babies, group switch |
| `compat/mekanism` | `enable_baby_enderman_invader` | Baby Enderman |
| `compat/mekanism` | `enable_baby_skeleton_invader` | Baby Skeleton |
| `compat/mekanism` | `enable_baby_stray_invader` | Baby Stray |
| `compat/mekanism` | `enable_baby_bogged_invader` | Baby Bogged |
| `compat/mekanism` | `enable_baby_wither_skeleton_invader` | Baby Wither Skeleton |
| `compat/mekanism_tools` | `enable_mekanism_tools_affixes` | Tools gear joins the affix loot pools |
| `compat/mekanism_tools` | `enable_mekanism_tools_salvaging` | Salvaging recipes for Tools gear |
| `compat/mekanism_tools` | `enable_mekanism_tools_gear_sets` | Invaders wear Tools sets |
| `compat/mekanism_energy_gear` | `block_mekanism_energy_gear_affixes` | Keep energy gear out of crafted affixes |
| `compat/ars_nouveau` | `enable_ars_nouveau_invaders` | Ars Nouveau Wilden, group switch |
| `compat/ars_nouveau` | `enable_wilden_hunter_invader` | Wilden Hunter |
| `compat/ars_nouveau` | `enable_wilden_stalker_invader` | Wilden Stalker |
| `compat/ars_nouveau` | `enable_wilden_guardian_invader` | Wilden Guardian |
| `compat/allthemodium` | `enable_allthemodium_invaders` | AllTheModium Piglich, group switch |
| `compat/allthemodium` | `enable_piglich_boss` | Piglich |
| `compat/vanilla_bosses` | `enable_vanilla_bosses` | Vanilla bosses, group switch |
| `compat/vanilla_bosses` | `enable_warden_boss` | Warden (10% chance) |
| `compat/vanilla_bosses` | `enable_wither_boss` | Wither (10% chance) |
| `compat/vanilla_bosses` | `enable_ender_dragon_boss` | Ender Dragon (first kill only) |

---

## Reloading

Two different things are involved:

- **The mod's TOML config**: NeoForge's `ConfigTracker` watches the file, so **saving it is enough**. No `/reload`,
  no restart.
- **Data packs**: `/reload` (or `F3+T` on the client) reloads resource and data packs. It has **nothing to do** with
  the TOML.

### By feature

| Feature | Requires | Why |
|---|---|---|
| Crafted gear (affixes / sockets / rarity / repair-combine) | **Save only** | Config is read live inside the event handler |
| Spawn egg unlocks (Warden / Elder Guardian) | **Save only** | Read live on interaction |
| Respawn health / named-mob peaceful protection | **Save only** | Read live inside the event handler |
| Named piglin no-zombify | **Save only** | Read live inside the mixin every tick |
| Monster cap (scaling / tier mode / bonus / ceiling) | **Save only** | Read on every spawn check; the memo key carries a generation counter |
| HUD sync / debug logging | **Save only** | Read live on each call |
| Gem drop multipliers | **Save only** | Rebuilt automatically on config and data-pack reload |
| **Any invader switch** (including the 15 per-entity ones) | **Data reload** | They feed data-pack conditions, evaluated once at parse time |
| Fake player tier inheritance | **Save only, but re-spawn the fake** | Decided when the fake joins |
| Adding/removing data files | **Data reload** | Data-pack content is only re-parsed on reload |

### Why the invader switches are the exception

They live in the TOML yet still need a data reload: each is an `ICondition` deciding whether a given invader JSON
entry is registered at all. Once the data pack has been parsed the registry is fixed, and flipping a switch does not
retract entries that are already registered - the pack has to be parsed again.

### Where the "no restart" guarantee comes from

Three constraints, all of which must hold:

| Constraint | If broken |
|---|---|
| Options are **not** marked `worldRestart` / `gameRestart` | NeoForge keeps the old cached value until a restart |
| Every use reads `Config.xxx()`; nothing is copied into a static field | The value freezes at class-init time |
| Cached data (e.g. the cap multiplier) is dropped on config change, and its key carries a **generation counter** | A stale value can still be hit within the same tick |

`build/test_hot_reload.js` statically checks the first two and the generation counter.

---

## Building from source

**Requirements**: JDK 21, Gradle 8.x

```bash
./gradlew build
```

Output: `build/libs/ApotheosisExpansion-1.21.1-neoforge-1.2.0.jar`

**Dependencies**: Apotheosis / Placebo / Apothic Attributes resolve from
[Shadow's Maven](https://maven.shadowsoffire.dev/releases/dev/shadowsoffire/):

```gradle
compileOnly "dev.shadowsoffire:Apotheosis:${mcVersion}-${apotheosisVersion}"
compileOnly "dev.shadowsoffire:Placebo:${mcVersion}-${placeboVersion}"
compileOnly "dev.shadowsoffire:ApothicAttributes:${mcVersion}-${apothicAttributesVersion}"
```

**A fresh clone needs no manually supplied jars.** If that repository is unreachable, download the
jars into `libs/` and temporarily switch the repository back to a flatDir.

### Gradle does not start on the development machine

Every Gradle version there fails with `Failed to load native library 'native-platform.dll'`
(an environment problem), so the project is actually compiled with `javac` directly:

```powershell
javac -encoding UTF-8 -proc:none --release 21 -sourcepath <empty-dir> -classpath $cp -d build/classes <sources...>
```

**`-sourcepath <empty-dir>` is mandatory.** Without it javac's implicit source discovery decompiles and
recompiles classes from the classpath jars into the output directory — in practice this pulled 1081
NeoForge classes into the output.

Both steps are scripted:

```bash
node build/compile.js     # compile into build/classes (finds JDK 21, assembles the classpath)
node build/package.js     # package into build/libs/
```

Packaging is equivalent to the manual form:

```bash
jar --create --file <target>.jar --manifest <manifest> -C <staging-dir> .
```

### Validation scripts

The static check scripts in `build/` cover data validity, mixin targets, condition codecs, the filter
predicate, the spawner-rune tooltips and README links:

```bash
node build/validate_invaders.js thermal
node build/verify_mixin.js
node build/test_filter_predicate.js
node build/verify_repair_combine.js
node build/verify_spawner_rune_tooltips.js
node build/test_spawner_rune_tooltips.js
node build/verify_readme.js
# ...
```

The project performs no automated in-game testing; validation is static-check based.

---

## Source layout

```
src/main/java/com/tamamo/apotheosisexpansion/
├── ApotheosisExpansion.java        Main class: config, listeners, client screen
├── Config.java                     ModConfigSpec (4 sections + per-mod compat sub-sections)
├── RaritySelector.java             Rarity roll with min/max clamping
├── CraftedAffixRoller.java         Affix and socket generation core
├── SpawnerUnlockHandler.java       Spawn egg unlock
├── RepairCombineInheritance.java   Affix inheritance on repair-combine
├── ClientIntegration.java          Client-only, isolated loading
├── compat/
│   ├── ThermalCompat.java              Condition type registration
│   ├── ConfigConditions.java           Maps switches to data-pack conditions
│   ├── EntityInvaderEnabled.java       Per-entity switch condition (entity parameter)
│   ├── InvaderLookup.java              Shared entry lookup and rarity choice
│   ├── InvaderSpawnerFilter.java       Spawner exclusion predicate
│   ├── VanillaBossHooks.java           Applies Apotheosis data to vanilla bosses
│   ├── VanillaBossEvents.java          Warden/wither/dragon spawn hooks
│   └── PiglichGearHook.java            Piglich random ATM gear + boss conversion
├── mixin/
│   ├── AbstractContainerMenuMixin.java Crafted-gear injection
│   ├── BossSpawnerTileMixin.java       Excludes our entries from spawner draws
│   └── CreativeTargetFilter.java       Bosses ignore creative/spectator
├── gems/
│   ├── GemWeightTweaker.java       Patches Gem.weights by reflection
│   ├── GemChanceTweaker.java       Patches GemLootModifier.entries by reflection
│   └── GemTweakApplier.java        Schedules both on data sync / config reload
└── spawnerrune/
    ├── SpawnerRuneIntegration.java Optional-dependency ModList gate (holds no such types)
    └── SpawnerRuneTooltips.java    Reads spawner_modifier recipes to build the tooltip lines
```

Data pack folders:

```
src/main/resources/data/apotheosis/apothic_invaders/
├── thermal/          7 entries
├── mekanism/         8 entries
├── ars_nouveau/      3 entries
├── allthemodium/     1 entry
└── vanilla_bosses/   4 entries
```
