# Configuration & Building

[中文](CONFIG.md) | **English**

[← Back to README](../README_en.md) · [Feature details](FEATURES_en.md)

---

## Config file

Located at `config/apotheosis_expansion-common.toml`: **4 sections, 37 entries**
(22 literal values + 15 per-entity switches, the latter only registered when their mod is present).

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
| `crafting` | `inherit_on_repair_combine` | Inherit affixes on repair-combine (bug-fix switch) |
| `rarity` | `min_rarity` | Lowest rarity that can be rolled |
| `rarity` | `max_rarity` | Highest rarity that can be rolled |
| `rarity` | `respect_world_tier` | Also clamp the cap to the world tier |
| `spawner` | `enable_warden_unlock` | Allow Warden eggs in spawners |
| `spawner` | `enable_elder_guardian_unlock` | Allow Elder Guardian eggs in spawners |
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

| Change | Requires |
|---|---|
| Crafting / spawn eggs / gem multipliers | Takes effect immediately in the config screen |
| Any invader switch | **Data reload** (`F3+T` or rejoin the world) |
| Adding/removing data files | Same as above |

Invader switches feed **data-pack conditions**, which are only evaluated on a data reload — a config
reload is not enough.

---

## Building from source

**Requirements**: JDK 21, Gradle 8.x

```bash
./gradlew build
```

Output: `build/libs/ApotheosisExpansion-1.21.1-neoforge-1.0.0.jar`

**Dependencies**: Apotheosis / Placebo / Apothic Attributes are resolved from a local `libs/` flatDir
repository (those jars are not reliably available on public Maven). If you prefer public repositories,
adjust `repositories` in `build.gradle`.

### Gradle does not start on the development machine

Every Gradle version there fails with `Failed to load native library 'native-platform.dll'`
(an environment problem), so the project is actually compiled with `javac` directly:

```powershell
javac -encoding UTF-8 -proc:none --release 21 -sourcepath <empty-dir> -classpath $cp -d build/classes <sources...>
```

**`-sourcepath <empty-dir>` is mandatory.** Without it javac's implicit source discovery decompiles and
recompiles classes from the classpath jars into the output directory — in practice this pulled 1081
NeoForge classes into the output.

Packaging:

```bash
jar --create --file <target>.jar --manifest <manifest> -C <staging-dir> .
```

### Validation scripts

`build/` holds 10 static check scripts covering data validity, mixin targets, condition codecs, the
filter predicate and README links:

```bash
node build/validate_invaders.js thermal
node build/verify_mixin.js
node build/test_filter_predicate.js
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
└── gems/
    ├── GemWeightTweaker.java       Patches Gem.weights by reflection
    ├── GemChanceTweaker.java       Patches GemLootModifier.entries by reflection
    └── GemTweakApplier.java        Schedules both on data sync / config reload
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
