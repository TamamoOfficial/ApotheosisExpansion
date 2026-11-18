package com.tamamo.apotheosisexpansion.spawnerrune;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.ModList;

/**
 * Decides whether the spawner-rune tooltip feature can run, by asking whether the class it needs actually exists.
 *
 * <h2>Why not {@code ModList.isLoaded}</h2>
 * {@code ModList.isLoaded("apothic_spawners")} returned false in a real client session that had the mod installed
 * (1.4.0, present in FML's own mod list). The same lookup also failed for {@code minihud} and {@code carpet} in this
 * pack - see the {@code CompatMixinPlugin} log lines, which fall back to the class check for exactly that reason. So
 * the mod-list answer cannot be trusted here, and a wrong "not loaded" silently disables the whole feature.
 *
 * <h2>What is asked instead</h2>
 * The question that actually matters is "can this JVM link {@code SpawnerRuneTooltips}", and the precise form of that
 * question is whether {@code ASObjects} resolves. Two probes are tried, cheapest and most reliable first:
 *
 * <ol>
 * <li><b>Class resource lookup</b> - {@code ASObjects.class} as a resource. This never defines the class, so it cannot
 * perturb Mixin or the loader cache, and it works as soon as the mod jar is on the classpath.</li>
 * <li><b>{@code ModList}</b> - only as a second opinion, because the resource check can miss a jar mounted through an
 * unusual locator. It is not trusted when it says "no".</li>
 * </ol>
 *
 * <p>The two are OR-ed rather than AND-ed: either signal is sufficient, and a false negative is the expensive mistake -
 * it turns the feature off with no error anywhere.
 */
public final class SpawnerRuneIntegration {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The mod whose {@code spawner_modifier} recipes describe the runes. */
    public static final String TARGET_MODID = "apothic_spawners";

    /**
     * A class that only exists when Apothic Spawners is installed.
     * <p>
     * Named as a string on purpose: referencing the type directly would make this class unloadable on a pack without
     * the mod, which is the very failure this gate exists to prevent.
     */
    private static final String PROBE_CLASS = "dev.shadowsoffire.apothic_spawners.ASObjects";

    /** Cached: the mod set cannot change while the game runs, and the probes are cheap but not free. */
    private static Boolean available;

    private SpawnerRuneIntegration() {}

    /**
     * Whether the tooltip class may be loaded.
     * <p>
     * Never throws: any failure to answer is reported as "available", so the feature is attempted and any real link
     * error surfaces as a normal crash log rather than as a silently missing tooltip.
     */
    public static boolean isAvailable() {
        if (available != null) {
            return available;
        }

        boolean byClass = classPresent();
        boolean byModList = modListSaysLoaded();

        available = byClass || byModList;

        if (available) {
            LOGGER.info("[ApotheosisExpansion] Spawner rune tooltips enabled (class probe: {}, mod list: {})",
                byClass, byModList);
        }
        else {
            LOGGER.info("[ApotheosisExpansion] Spawner rune tooltips unavailable: neither {} nor a '{}' mod entry was found",
                PROBE_CLASS, TARGET_MODID);
        }

        return available;
    }

    /** Asks the class loader whether the probe class is visible, <b>without</b> defining it. */
    private static boolean classPresent() {
        String resource = PROBE_CLASS.replace('.', '/') + ".class";
        try {
            ClassLoader loader = SpawnerRuneIntegration.class.getClassLoader();
            if (loader == null) {
                return false;
            }
            // getResource does not load the class: it only asks whether the bytes are reachable.
            return loader.getResource(resource) != null;
        }
        catch (RuntimeException | LinkageError ex) {
            LOGGER.debug("[ApotheosisExpansion] Class probe for {} failed", PROBE_CLASS, ex);
            return false;
        }
    }

    /**
     * The secondary signal.
     * <p>
     * Kept because a jar mounted through an unusual locator may not be visible to {@link #classPresent()}, but only ever
     * used to turn the feature <i>on</i>: this method returned false for installed mods in practice.
     */
    private static boolean modListSaysLoaded() {
        try {
            ModList modList = ModList.get();
            return modList != null && modList.isLoaded(TARGET_MODID);
        }
        catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }
}
