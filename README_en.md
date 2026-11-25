# Apotheosis Expansion

[中文](README.md) | **English**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62b47a)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.259-e89b4a)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

An addon for [Apotheosis](https://github.com/Shadows-of-Fire/Apotheosis). It fills the gaps Apotheosis
leaves open, fixes a few behaviours that feel wrong in play, and adds Apotheosis Invader (BOSS)
integration for mobs from several other mods.

Every feature can be toggled independently from the in-game config screen.

> For implementation notes and design trade-offs, see **[Feature Details](docs/FEATURES_en.md)**.

---

## Modules

### Crafting & spawning

| Module | Description |
|---|---|
| **Crafted gear rolls affixes** | Player-crafted equipment gets Apotheosis affixes and gem sockets |
| **Spawn egg unlock** | Lifts the spawner restriction on Warden and Elder Guardian eggs |
| **Repair-combine fix** | Fixes affixes being re-rolled when combining two items; a merge can no longer mint affixes from nothing (exploit fix) |
| **Gem drop tuning** | Scales gem drop rates and weights by multiplier |
| **Gem compendium (JEI/EMI)** | One page per gem per purity: which dimensions/tiers it drops in and the exact bonuses; with both viewers installed only EMI registers |
| **Shared monster budget** | Players sharing a spawn platform raise the monster cap together; optionally across tiers |
| **Spawner rune stats** | Lists what a rune actually changes in its tooltip, read from the loaded data pack |

### Player fixes

| Module | Description |
|---|---|
| **Respawn health fix** | Respawns at the real maximum health instead of a flat 20 HP |
| **Named-mob peaceful protection** | Player-named mobs survive switching the world to Peaceful |
| **Named piglins never zombify** | Player-named piglins and brutes stay piglins in the Overworld |
| **Fake player tier inheritance** | Fake players adopt their summoner's world tier, so the shared cap works for them too |

### Mod integration

Adds Apotheosis Invaders for mobs from other mods. **No mod is a hard dependency** — when one is
absent its whole group disappears.

| Mod | Mobs | Notes |
|---|---|---|
| **Thermal** | Basalz / Blizz / Blitz | 7 entries across several biomes |
| **Mekanism Additions** | Five baby mobs | Weight is 1/3 of the adult (1/4 for Wither Skeleton) |
| **Ars Nouveau** | Wilden Hunter / Stalker / Guardian | Spawned by Wilden Den structures |
| **AllTheModium** | Piglich | Ancient Pyramid; also restores its random ATM gear |
| **Mekanism: Tools** | Material gear (not mobs) | Affix loot pools + salvaging recipes + invader gear sets |

### Vanilla bosses

| Module | Description |
|---|---|
| **Warden** | 10% chance when summoned by a shrieker |
| **Wither** | 10% chance when player-built, tiered by dimension |
| **Ender Dragon** | First dragon of the save only, always the highest rarity |
| **Targeting fix** | Bosses no longer force-target creative/spectator players |

### HUD compatibility

| Module | Description |
|---|---|
| **Cap sync to HUD** | Optionally makes MiniHUD / Servux show the widened real cap instead of the vanilla 70 |

---

## Installation

**Required**

| Mod | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235+ |
| Apotheosis | 8.9.0+ |
| Placebo | 9.9.2+ |
| Apothic Attributes | 2.11.0+ |

**Optional** (the matching integration simply disappears when absent)

- Thermal Series / Mekanism Additions / Ars Nouveau / AllTheModium
- Ancient Reforging — makes the first Ender Dragon use the Ancient rarity

Drop the jar into `mods/`. Required on both client and server.

---

## Configuration

Config file `config/apotheosis_expansion-common.toml`: 5 sections, 47 entries.
The in-game screen groups switches **by mod**; absent mods produce no empty switches.

Full key reference: [Config & Build](docs/CONFIG_en.md).

**Reloading**: almost every option **applies as soon as you save it** — no restart, no command. The
exception is the invader switches, which feed data-pack conditions and need a data reload (`F3+T` or
`/reload`). Per-feature breakdown: [Config & Build](docs/CONFIG_en.md#reloading).

---

## Building from source

```bash
./gradlew build
```

Output: `build/libs/ApotheosisExpansion-1.21.1-neoforge-1.2.0.jar`

Dependencies are downloaded by Gradle from
[Shadow's Maven](https://maven.shadowsoffire.dev/releases/dev/shadowsoffire/), so **a fresh clone
needs no manually supplied jars**. Gradle does not start on the development machine
(`native-platform.dll`), so `javac` is used directly — details in
[Feature Details · Config](docs/CONFIG_en.md).

---

## Known limitations

1. Dragon equipment is **invisible on the model** (no armour layer), though attributes and affixes apply
2. Dragon equipment is **not dropped on death** (separate death handling)
3. `pinnacle`-tier numbers are **unverified in play**
4. Targets 1.21.1 / NeoForge only
5. Ancient Reforging path is **unverified in play**

---

## License

[MIT](LICENSE) © Tamamo

This is an addon for [Apotheosis](https://github.com/Shadows-of-Fire/Apotheosis) and contains none of
its code or assets.
