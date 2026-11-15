package com.tamamo.apotheosisexpansion.compat;

import com.mojang.serialization.MapCodec;

import com.tamamo.apotheosisexpansion.Config;

import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Loading conditions that mirror this mod's {@code [compat]} toggles into data pack loading.
 * <p>
 * <b>Why conditions rather than runtime checks.</b> Apotheosis' Invader registry is a Placebo
 * {@code DynamicRegistry} rebuilt from data files, and it runs {@code JsonUtil.checkConditions} before decoding each
 * entry. Conditions are therefore the only supported way to make an entry disappear: the registry exposes no removal
 * API, and entries cannot be dropped from a game-bus listener.
 * <p>
 * <b>Why these are not hot-reloadable.</b> Conditions are evaluated while data packs are parsed. NeoForge clears the
 * cached config values on {@code ModConfigEvent.Reloading}, so the condition's result would change, but the already
 * parsed entries stay in the registry until the data packs are read again. Toggling therefore needs a data reload
 * ({@code F3+T} on a client, or rejoining a world).
 * <p>
 * Each type is registered in {@link ThermalCompat}; the JSON files reference them by id.
 */
public final class ConfigConditions {

    /** {@code {"type": "apotheosis_expansion:thermal_invaders_enabled"}} */
    public static final MapCodec<ThermalInvadersEnabled> THERMAL_INVADERS =
        MapCodec.unit(ThermalInvadersEnabled.INSTANCE).stable();

    /** {@code {"type": "apotheosis_expansion:mekanism_invaders_enabled"}} */
    public static final MapCodec<MekanismInvadersEnabled> MEKANISM_INVADERS =
        MapCodec.unit(MekanismInvadersEnabled.INSTANCE).stable();

    /** {@code {"type": "apotheosis_expansion:allthemodium_invaders_enabled"}} */
    public static final MapCodec<AllthemodiumInvadersEnabled> ALLTHEMODIUM_INVADERS =
        MapCodec.unit(AllthemodiumInvadersEnabled.INSTANCE).stable();

    /** {@code {"type": "apotheosis_expansion:vanilla_bosses_enabled"}} */
    public static final MapCodec<VanillaBossesEnabled> VANILLA_BOSSES =
        MapCodec.unit(VanillaBossesEnabled.INSTANCE).stable();

    /** {@code {"type": "apotheosis_expansion:ars_nouveau_invaders_enabled"}} */
    public static final MapCodec<ArsNouveauInvadersEnabled> ARS_NOUVEAU_INVADERS =
        MapCodec.unit(ArsNouveauInvadersEnabled.INSTANCE).stable();

    private ConfigConditions() {}

    /**
     * Reads a config toggle without letting an exception escape into the data pack load.
     * <p>
     * {@code ConfigValue.get()} throws {@code IllegalStateException} if the config has not been loaded yet. The COMMON
     * config is loaded long before any data pack is parsed, so this should not happen in practice - but if it did, an
     * escaping exception would abort the entire data pack load, not just this one entry. Failing open keeps the
     * feature on, matching the config default.
     */
    private static boolean readSafely(java.util.function.BooleanSupplier toggle) {
        try {
            return toggle.getAsBoolean();
        }
        catch (IllegalStateException ex) {
            return true;
        }
    }

    public record ThermalInvadersEnabled() implements ICondition {

        public static final ThermalInvadersEnabled INSTANCE = new ThermalInvadersEnabled();

        @Override
        public boolean test(IContext context) {
            return readSafely(Config::enableThermalInvaders);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return THERMAL_INVADERS;
        }

        @Override
        public String toString() {
            return "apotheosis_expansion:thermal_invaders_enabled";
        }
    }

    public record MekanismInvadersEnabled() implements ICondition {

        public static final MekanismInvadersEnabled INSTANCE = new MekanismInvadersEnabled();

        @Override
        public boolean test(IContext context) {
            return readSafely(Config::enableMekanismInvaders);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return MEKANISM_INVADERS;
        }

        @Override
        public String toString() {
            return "apotheosis_expansion:mekanism_invaders_enabled";
        }
    }

    public record AllthemodiumInvadersEnabled() implements ICondition {

        public static final AllthemodiumInvadersEnabled INSTANCE = new AllthemodiumInvadersEnabled();

        @Override
        public boolean test(IContext context) {
            return readSafely(Config::enableAllthemodiumInvaders);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return ALLTHEMODIUM_INVADERS;
        }

        @Override
        public String toString() {
            return "apotheosis_expansion:allthemodium_invaders_enabled";
        }
    }

    public record VanillaBossesEnabled() implements ICondition {

        public static final VanillaBossesEnabled INSTANCE = new VanillaBossesEnabled();

        @Override
        public boolean test(IContext context) {
            return readSafely(Config::enableVanillaBosses);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return VANILLA_BOSSES;
        }

        @Override
        public String toString() {
            return "apotheosis_expansion:vanilla_bosses_enabled";
        }
    }

    public record ArsNouveauInvadersEnabled() implements ICondition {

        public static final ArsNouveauInvadersEnabled INSTANCE = new ArsNouveauInvadersEnabled();

        @Override
        public boolean test(IContext context) {
            return readSafely(Config::enableArsNouveauInvaders);
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return ARS_NOUVEAU_INVADERS;
        }

        @Override
        public String toString() {
            return "apotheosis_expansion:ars_nouveau_invaders_enabled";
        }
    }
}
