package com.tamamo.apotheosisexpansion.gemcompendium;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.ModList;

/**
 * Decides which recipe viewer, if either, should publish the gem compendium.
 *
 * <h2>The rule: EMI wins, and JEI is then silent</h2>
 * With <em>both</em> installed only the EMI pages are registered. EMI ships a JEMI bridge that imports every JEI
 * recipe plugin into its own index, so registering the JEI pages as well would list every gem page twice - once
 * natively and once through the bridge. Disabling the JEI half when EMI is present is the only way to avoid that
 * without reaching into another mod's internals.
 * <p>
 * The consequence for the JEI plugin is that it must still be <em>loadable</em> (JEI instantiates every
 * {@code @JeiPlugin} it finds) but register nothing. That is what {@link #useJei()} answers.
 *
 * <h2>Why presence is probed two ways</h2>
 * {@code ModList.isLoaded} is the documented answer, but this pack has already produced false negatives for it - see
 * {@code SpawnerRuneIntegration}, which needed a class-resource probe for the very same reason. So the question asked
 * here is the one that actually matters: "would this JVM be able to link the viewer's plugin interface". The class
 * lookup answers that without defining anything, and {@code ModList} is kept as a second opinion that can only turn
 * the answer <em>on</em>.
 *
 * <h2>Why this class holds no viewer types</h2>
 * {@code Config} calls {@link #anyViewerPresent()} while building the config spec, long before any viewer is loaded,
 * and on a dedicated server where neither viewer exists. Naming a JEI or EMI type here would make that call fail with
 * a {@code NoClassDefFoundError}; the probe strings are therefore plain constants.
 */
public final class GemCompendiumGate {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** JEI's mod id. */
    public static final String JEI_MODID = "jei";

    /** EMI's mod id. */
    public static final String EMI_MODID = "emi";

    /** Interface every JEI plugin implements; present exactly when JEI is. */
    private static final String JEI_PROBE = "mezz.jei.api.IModPlugin";

    /** Interface every EMI plugin implements; present exactly when EMI is. */
    private static final String EMI_PROBE = "dev.emi.emi.api.EmiPlugin";

    /** The mod set cannot change while the game runs, so each answer is computed once. */
    private static Boolean jeiPresent;
    private static Boolean emiPresent;

    private GemCompendiumGate() {}

    /** Whether JEI should publish the pages: JEI installed, and EMI is not. */
    public static boolean useJei() {
        boolean use = jeiPresent() && !emiPresent();
        LOGGER.debug("[ApotheosisExpansion] Gem compendium via JEI: {}", use);
        return use;
    }

    /** Whether EMI should publish the pages: EMI installed, regardless of JEI. */
    public static boolean useEmi() {
        return emiPresent();
    }

    /** Whether at least one viewer is installed, i.e. whether the feature can do anything at all. */
    public static boolean anyViewerPresent() {
        return jeiPresent() || emiPresent();
    }

    /** Sets the backup signal for both viewers, so the probe stays the primary one. */
    public static boolean jeiPresent() {
        if (jeiPresent == null) {
            boolean byClass = classPresent(JEI_PROBE);
            jeiPresent = byClass || installed(JEI_MODID);
            if (jeiPresent) {
                LOGGER.info("[ApotheosisExpansion] JEI detected (class probe: {}, mod list: {})",
                    byClass, installed(JEI_MODID));
            }
        }
        return jeiPresent;
    }

    /** @see #jeiPresent() */
    public static boolean emiPresent() {
        if (emiPresent == null) {
            boolean byClass = classPresent(EMI_PROBE);
            emiPresent = byClass || installed(EMI_MODID);
            if (emiPresent) {
                LOGGER.info("[ApotheosisExpansion] EMI detected (class probe: {}, mod list: {})",
                    byClass, installed(EMI_MODID));
            }
        }
        return emiPresent;
    }

    /**
     * Asks the class loader whether the probe interface is visible, <b>without</b> defining it.
     * <p>
     * {@code getResource} only checks whether the bytes are reachable. Defining the class here would be harmless for
     * a plain interface, but the same helper is used for Mixin-adjacent targets elsewhere in this mod, so the habit is
     * kept consistent.
     */
    private static boolean classPresent(String binaryName) {
        String resource = binaryName.replace('.', '/') + ".class";
        try {
            ClassLoader loader = GemCompendiumGate.class.getClassLoader();
            return loader != null && loader.getResource(resource) != null;
        }
        catch (RuntimeException | LinkageError ex) {
            LOGGER.debug("[ApotheosisExpansion] Class probe for {} failed", binaryName, ex);
            return false;
        }
    }

    /** Never throws: an unavailable {@code ModList} means "cannot tell", and the probe already answered. */
    private static boolean installed(String modId) {
        try {
            ModList modList = ModList.get();
            return modList != null && modList.isLoaded(modId);
        }
        catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }
}
