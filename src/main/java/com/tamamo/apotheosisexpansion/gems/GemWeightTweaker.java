package com.tamamo.apotheosisexpansion.gems;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.mojang.logging.LogUtils;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.tiers.TieredWeights;
import dev.shadowsoffire.apotheosis.tiers.TieredWeights.Weight;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Scales the relative drop weight of individual gems.
 * <p>
 * Apotheosis exposes {@link Gem#weights()} as a read-only accessor and never offers a setter, so the field is written
 * reflectively. Two details make the repeated application safe:
 * <ul>
 * <li>The pristine weights are cached the first time a gem object is seen, so applying multiplier {@code m} ten times
 * in a row yields the same result as applying it once. Without this, {@code /reload} would compound the multiplier on
 * every invocation.</li>
 * <li>The cache is keyed by the gem's <em>object identity</em>: a data pack reload builds brand-new {@link Gem}
 * instances, so stale entries are never applied to a rebuilt registry.</li>
 * </ul>
 */
public final class GemWeightTweaker {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code Gem.weights}, resolved once. Null when the field could not be found. */
    private static final Field WEIGHTS = resolveWeightsField();

    private static final Map<ResourceLocation, TieredWeights> PRISTINE = new HashMap<>();
    private static final Map<ResourceLocation, Gem> LAST_SCALED = new HashMap<>();

    private GemWeightTweaker() {}

    private static Field resolveWeightsField() {
        try {
            Field f = Gem.class.getDeclaredField("weights");
            f.setAccessible(true);
            return f;
        }
        catch (ReflectiveOperationException | RuntimeException ex) {
            // Never throw from a static initialiser here: a changed Apotheosis version must not crash the game on
            // startup. Weight tweaks simply become unavailable while everything else keeps working.
            LOGGER.error("[ApotheosisExpansion] Could not access Gem.weights; per-gem weight tweaks are disabled.", ex);
            return null;
        }
    }

    /** True when per-gem weight tweaking is usable with the installed Apotheosis. */
    public static boolean isAvailable() {
        return WEIGHTS != null;
    }

    /**
     * Applies the multipliers to every registered gem.
     *
     * @param multipliers per-gem multipliers, keyed by full id (short forms are also accepted)
     */
    public static void apply(Map<String, Double> multipliers) {
        if (WEIGHTS == null) {
            return;
        }

        int touched = 0;
        int skipped = 0;

        for (Gem gem : GemRegistry.INSTANCE.getValues()) {
            ResourceLocation id = GemRegistry.INSTANCE.getKey(gem);
            if (id == null) {
                skipped++;
                continue;
            }
            double mult = resolveMultiplier(multipliers, id);

            TieredWeights base;
            if (LAST_SCALED.get(id) == gem) {
                // Same object as last time: rescale from the cached original rather than the already-scaled value.
                base = PRISTINE.get(id);
            }
            else {
                base = gem.weights();
                PRISTINE.put(id, base);
                LAST_SCALED.put(id, gem);
            }

            try {
                WEIGHTS.set(gem, scale(base, mult));
                touched++;
            }
            catch (IllegalAccessException ex) {
                LOGGER.error("[ApotheosisExpansion] Failed to apply weight multiplier to gem {}", id, ex);
            }
        }

        LOGGER.info("[ApotheosisExpansion] Gem weight tweaks applied: {} gem(s) updated, {} skipped", touched, skipped);
    }

    /**
     * Restores every gem to its original weights.
     * <p>
     * Used when the feature is switched off, so that disabling the toggle really puts Apotheosis back the way it was
     * instead of leaving the last scaled values in place.
     */
    public static void restore() {
        if (WEIGHTS == null) {
            return;
        }
        int restored = 0;
        for (Map.Entry<ResourceLocation, Gem> e : LAST_SCALED.entrySet()) {
            TieredWeights pristine = PRISTINE.get(e.getKey());
            Gem gem = e.getValue();
            if (pristine == null || gem == null) {
                continue;
            }
            try {
                WEIGHTS.set(gem, pristine);
                restored++;
            }
            catch (IllegalAccessException ex) {
                LOGGER.error("[ApotheosisExpansion] Failed to restore weights of gem {}", e.getKey(), ex);
            }
        }
        if (restored > 0) {
            LOGGER.info("[ApotheosisExpansion] Restored original weights for {} gem(s)", restored);
        }
    }

    /**
     * Resolves a gem's multiplier, accepting the full id, the namespaced path, or the bare file name.
     * This mirrors the original mod so hand-written files keep working.
     */
    private static double resolveMultiplier(Map<String, Double> cfg, ResourceLocation id) {
        Double d = cfg.get(id.toString());
        if (d == null) {
            d = cfg.get(id.getPath());
        }
        if (d == null) {
            int slash = id.getPath().lastIndexOf('/');
            String shortName = slash >= 0 ? id.getPath().substring(slash + 1) : id.getPath();
            d = cfg.get(shortName);
        }
        return d == null ? 1.0D : d;
    }

    private static TieredWeights scale(TieredWeights weights, double mult) {
        Map<WorldTier, Weight> out = new HashMap<>();
        weights.weights().forEach((tier, w) -> {
            if (mult <= 0.0D) {
                out.put(tier, new Weight(0, 0.0F));
            }
            else {
                int scaled = (int) Math.round(w.weight() * mult);
                out.put(tier, new Weight(Math.max(0, scaled), w.quality()));
            }
        });
        return new TieredWeights(out);
    }
}
