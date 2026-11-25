package com.tamamo.apotheosisexpansion.compat;

import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registration for the mod-compatibility layer.
 * <p>
 * <b>Thermal.</b> Apotheosis 8.9.0 ships no Thermal Series content at all - its
 * {@code data/apotheosis/apothic_invaders/}, {@code apothic_elites/} and {@code gear_sets/} trees reference only
 * vanilla and Twilight Forest mobs, and a full-text scan of the jar turns up no {@code thermal} / {@code cofh} /
 * {@code basalz} / {@code blizz} / {@code blitz} string. The three Thermal elementals are therefore added here as
 * plain datapack entries.
 * <p>
 * <b>Mekanism Additions.</b> The baby mobs have existed since MC 1.12 but moved into the official
 * {@code mekanismadditions} module in MK10, so they are absent from the Mekanism jar that this mod compiles against.
 * Apotheosis has no entries for them either, so they are added the same way.
 * <p>
 * Every entry lives under {@code data/apotheosis/apothic_invaders/} in this mod's own namespace and carries a
 * {@code neoforge:mod_loaded} condition for the target mod plus one of this mod's config conditions, so it loads only
 * when the target mod is present and the feature is switched on.
 */
public final class ThermalCompat {

    /**
     * The Condition registry is a NeoForge registry, so it must be registered on the <b>mod</b> event bus, not the
     * game bus.
     */
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
        DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, "apotheosis_expansion");

    /** {@code {"type": "apotheosis_expansion:thermal_invaders_enabled"}} in a data file's condition list. */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.ThermalInvadersEnabled>> THERMAL_INVADERS_ENABLED =
        CONDITION_CODECS.register("thermal_invaders_enabled", () -> ConfigConditions.THERMAL_INVADERS);

    /** {@code {"type": "apotheosis_expansion:mekanism_invaders_enabled"}} in a data file's condition list. */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.MekanismInvadersEnabled>> MEKANISM_INVADERS_ENABLED =
        CONDITION_CODECS.register("mekanism_invaders_enabled", () -> ConfigConditions.MEKANISM_INVADERS);

    /** {@code {"type": "apotheosis_expansion:ars_nouveau_invaders_enabled"}} in a data file's condition list. */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.ArsNouveauInvadersEnabled>> ARS_NOUVEAU_INVADERS_ENABLED =
        CONDITION_CODECS.register("ars_nouveau_invaders_enabled", () -> ConfigConditions.ARS_NOUVEAU_INVADERS);

    /**
     * {@code {"type": "apotheosis_expansion:entity_invader_enabled", "entity": "thermal:basalz"}}.
     * <p>
     * One registry entry covers every per-mob switch; the entity id is a field on the condition rather than a separate
     * type, so adding a mob to {@link EntityInvaderEnabled#SUPPORTED} does not touch the registry.
     */
    /** {@code {"type": "apotheosis_expansion:allthemodium_invaders_enabled"}} */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.AllthemodiumInvadersEnabled>> ALLTHEMODIUM_INVADERS_ENABLED =
        CONDITION_CODECS.register("allthemodium_invaders_enabled", () -> ConfigConditions.ALLTHEMODIUM_INVADERS);

    /** {@code {"type": "apotheosis_expansion:vanilla_bosses_enabled"}} */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.VanillaBossesEnabled>> VANILLA_BOSSES_ENABLED =
        CONDITION_CODECS.register("vanilla_bosses_enabled", () -> ConfigConditions.VANILLA_BOSSES);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<EntityInvaderEnabled>> ENTITY_INVADER_ENABLED =
        CONDITION_CODECS.register("entity_invader_enabled", () -> EntityInvaderEnabled.CODEC);

    /** {@code {"type": "apotheosis_expansion:mekanism_tools_affixes_enabled"}} */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.MekanismToolsAffixesEnabled>> MEKANISM_TOOLS_AFFIXES_ENABLED =
        CONDITION_CODECS.register("mekanism_tools_affixes_enabled", () -> ConfigConditions.MEKANISM_TOOLS_AFFIXES);

    /** {@code {"type": "apotheosis_expansion:mekanism_tools_salvaging_enabled"}} */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.MekanismToolsSalvagingEnabled>> MEKANISM_TOOLS_SALVAGING_ENABLED =
        CONDITION_CODECS.register("mekanism_tools_salvaging_enabled", () -> ConfigConditions.MEKANISM_TOOLS_SALVAGING);

    /** {@code {"type": "apotheosis_expansion:mekanism_tools_gear_sets_enabled"}} */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.MekanismToolsGearSetsEnabled>> MEKANISM_TOOLS_GEAR_SETS_ENABLED =
        CONDITION_CODECS.register("mekanism_tools_gear_sets_enabled", () -> ConfigConditions.MEKANISM_TOOLS_GEAR_SETS);

    /** {@code {"type": "apotheosis_expansion:mekanism_energy_gear_blocked"}} */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigConditions.MekanismEnergyGearBlocked>> MEKANISM_ENERGY_GEAR_BLOCKED =
        CONDITION_CODECS.register("mekanism_energy_gear_blocked", () -> ConfigConditions.MEKANISM_ENERGY_GEAR_BLOCKED);

    private ThermalCompat() {}

    public static void register(IEventBus modBus) {
        CONDITION_CODECS.register(modBus);
    }
}
