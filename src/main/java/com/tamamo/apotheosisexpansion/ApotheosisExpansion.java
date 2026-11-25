package com.tamamo.apotheosisexpansion;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.tamamo.apotheosisexpansion.compat.ThermalCompat;
import com.tamamo.apotheosisexpansion.gems.GemTweakApplier;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Apotheosis Expansion — a companion mod for Apotheosis with four feature groups:
 * <ul>
 * <li><b>Crafting</b> — player-crafted equipment receives random Apotheosis affixes and gem sockets.</li>
 * <li><b>Spawner</b> — the vanilla Warden spawn egg is allowed on mob spawners again.</li>
 * <li><b>Gem Drops</b> — gem drop chance and per-gem weights are tunable.</li>
 * <li><b>Compat</b> — Apotheosis Invaders for the Thermal Series elementals, which Apotheosis itself does not cover.</li>
 * </ul>
 * Every feature can be toggled from the in-game mod list config screen. Live reload is
 * provided by NeoForge itself rather than by this mod: {@code ConfigTracker} installs a night-config {@code
 * FileWatcher} on the TOML file, and {@code ModConfigSpec.acceptConfig} calls {@code afterReload()} which clears the
 * cached value of every config entry that is not marked {@code worldRestart}/{@code gameRestart}. Since none of the
 * entries here request a restart, editing the file by hand and hitting "Done" in the config screen both behave the
 * same way.
 * <p>
 * The one exception is {@code [compat] enable_thermal_invaders}: it feeds a NeoForge loading condition, which is
 * evaluated while data packs are parsed, so changing it needs a data reload ({@code F3+T} or rejoining a world)
 * rather than only a config reload. See {@code compat.ConfigEnabledCondition}.
 * <p>
 * The mod hard-depends on Apotheosis at runtime; {@code neoforge.mods.toml} declares that so users get a clear
 * dependency error instead of a {@code NoClassDefFoundError} deep inside a crafting click.
 */
@Mod(ApotheosisExpansion.MODID)
public class ApotheosisExpansion {

    public static final String MODID = "apotheosis_expansion";

    private static final Logger LOGGER = LogUtils.getLogger();

    public ApotheosisExpansion(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "apotheosis_expansion-common.toml");

        // NeoForge loading condition backing the Thermal invader data files. Registered on the mod bus because the
        // Condition registry is a NeoForge registry. Registering the type is enough - the data files reference it by
        // id, and its result is evaluated when the data packs are parsed.
        ThermalCompat.register(modBus);

        // Game-bus listeners: spawner interaction and the egg tooltip line.
        // Registered unconditionally; each handler checks its own config toggle so switching the feature off at
        // runtime takes effect without re-registering anything.
        NeoForge.EVENT_BUS.register(new SpawnerUnlockHandler());

        // Respawn health: vanilla reads max health before gear modifiers are back, so players with
        // Apotheosis +max health affixes come back at a flat 20 HP. See RespawnHealthHandler.
        NeoForge.EVENT_BUS.register(new RespawnHealthHandler());

        // Named hostile mobs stay alive when the world is switched to Peaceful. Uses only the supported
        // MobDespawnEvent hook, so no vanilla method is overwritten; see NamedMobPeacefulProtection.
        NeoForge.EVENT_BUS.register(new NamedMobPeacefulProtection());

        // Vanilla boss conversion (warden / wither / ender dragon). These mobs never pass through
        // Apotheosis own invader gate, so they need dedicated hooks. The handler checks each toggle
        // itself, so switching a feature off at runtime needs no re-registration.
        NeoForge.EVENT_BUS.register(new com.tamamo.apotheosisexpansion.compat.VanillaBossEvents());

        // Gem drop tweaks, ported from the Gem Tweaks mod. Both targets are data pack content (GemRegistry and the
        // GemLootModifier instances are rebuilt on every reload), so the tweaks are applied from OnDatapackSyncEvent
        // rather than at construction time.
        NeoForge.EVENT_BUS.addListener(GemTweakApplier::onDatapackSync);

        // Spawn-lane statistics. Only does work when debug_mob_cap is on, so this listener is free in normal play.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
            com.tamamo.apotheosisexpansion.spawn.SpawnLanes.debugTick(
                event.getServer().overworld().getGameTime(), 200));

        // Client-only extension point: gives the mod a working "Config" button in the mod list.
        // Without an explicit IConfigScreenFactory the button stays disabled, because NeoForge only registers the
        // generic ConfigurationScreen for its own container.
        if (FMLEnvironment.dist.isClient()) {
            ClientIntegration.registerConfigScreen(container);

            // Spawner rune tooltips need a recipe manager and a tooltip renderer, so they are client-only. The shared
            // half (cache invalidation on data pack sync) is registered by SpawnerRuneIntegration, whose class holds no
            // Apothic Spawners types and therefore stays safe when that mod is absent.
            if (com.tamamo.apotheosisexpansion.spawnerrune.SpawnerRuneIntegration.isAvailable()) {
                ClientIntegration.registerSpawnerRuneTooltips();
            }
        }

        modBus.addListener(this::onConfigChanged);
    }

    /**
     * Called on the initial load ({@link ModConfigEvent.Loading}) and on every reload
     * ({@link ModConfigEvent.Reloading}) — the latter fires both for the config screen's "Done" button and for an
     * external edit caught by FML's file watcher.
     * <p>
     * The config values may only be read from inside this listener: at mod-construction time the COMMON config has not
     * been loaded yet, and {@code ConfigValue.get()} would throw {@code IllegalStateException}.
     */
    private void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != Config.SPEC) {
            return;
        }
        String phase = event instanceof ModConfigEvent.Reloading ? "reloaded" : "loaded";
        LOGGER.info("[ApotheosisExpansion] Config {}: affixes={}, sockets={}, rarity=[{}, {}], warden_unlock={}, elder_guardian_unlock={}, respawn_health_fix={}, gem_tweaks={} x{}, thermal_invaders={}, mekanism_invaders={}, ars_nouveau_invaders={}",
            phase, Config.enableAffixes(), Config.enableSockets(), Config.minRarity(), Config.maxRarity(),
            Config.enableWardenUnlock(), Config.enableElderGuardianUnlock(), Config.enableRespawnHealthFix(),
            Config.enableGemTweaks(), Config.gemDropChanceMultiplier(), Config.enableThermalInvaders(), Config.enableMekanismInvaders(), Config.enableArsNouveauInvaders());
        LOGGER.info("[ApotheosisExpansion] Named-mob peaceful protection: {}; named-piglin no-zombify: {}",
            Config.enableNamedPeacefulProtection(), Config.enableNamedPiglinNoZombify());
        LOGGER.info("[ApotheosisExpansion] Multiplayer monster cap: {} (all tiers: {}, bonus {} per extra player, ceiling {} players)",
            Config.enableMobCapScaling(), Config.mobCapCountAllTiers(),
            Config.mobCapBonusPerPlayer(), Config.mobCapMaxPlayers());
        LOGGER.info("[ApotheosisExpansion] Extra spawn lanes: {} (regional difficulty >= {}, real players only: {})",
            Config.enableSpawnLanes(), Config.spawnLanesMinRegionalDifficulty(),
            Config.spawnLanesRealPlayersOnly());
        LOGGER.info("[ApotheosisExpansion] Per-entity invader switches: {}",
            Config.invaderEntityIds().stream().filter(Config::isEntityInvaderEnabled).sorted().toList());
        LOGGER.info("[ApotheosisExpansion] Mekanism: Tools integration: affixes={}, salvaging={}, gear_sets={}; energy gear blocked={}",
            Config.enableMekanismToolsAffixes(), Config.enableMekanismToolsSalvaging(),
            Config.enableMekanismToolsGearSets(), Config.blockMekanismEnergyGearAffixes());

        // Applied on load AND on reload. Doing it only on reload left debug_mob_cap= true in the file doing nothing
        // until the user happened to change something, which is exactly the "my setting is ignored" report this is
        // meant to avoid.
        com.tamamo.apotheosisexpansion.spawn.MobCapScaler.setDebug(Config.debugMobCap());
        com.tamamo.apotheosisexpansion.spawn.MobCapScaler.clearCache();
        // Spawn lanes share debug_mob_cap: one switch for "show me why spawning did or did not happen" is easier to
        // find than two, and the counters are reset here so a reload starts a clean measurement window.
        com.tamamo.apotheosisexpansion.spawn.SpawnLanes.setDebug(Config.debugMobCap());
        com.tamamo.apotheosisexpansion.spawn.SpawnLanes.resetStats();

        // The gem tweaks bake their values into Apotheosis' live objects, so a config change alone does nothing.
        // Re-running them here is what makes the toggle and the multiplier hot-reloadable.
        // Skips silently until a server exists, since the loot modifier manager is only available then.
        if (event instanceof ModConfigEvent.Reloading) {
            GemTweakApplier.applyAll();
        }
    }
}
