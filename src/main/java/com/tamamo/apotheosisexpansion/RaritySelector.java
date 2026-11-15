package com.tamamo.apotheosisexpansion;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import net.minecraft.resources.ResourceLocation;

/**
 * Picks the {@link LootRarity} used for a crafted item and applies the configured min/max bounds.
 * <p>
 * Strategy (as agreed): roll normally against the Apotheosis registry — which honours the player's World Tier and
 * luck through {@link GenContext} — and then <em>clamp</em> the result into {@code [min_rarity, max_rarity]}.
 * Clamping instead of pre-filtering keeps the tier weighting intact and can never produce a null result.
 */
public final class RaritySelector {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RaritySelector() {}

    /**
     * Rolls a rarity for the given context and clamps it into the configured bounds.
     *
     * @param ctx the generation context (random source, world tier, luck, dimension, biome, stages)
     * @return a bound rarity, or {@code null} if the registry has not loaded yet
     */
    @Nullable
    public static LootRarity select(GenContext ctx) {
        RarityRegistry registry = RarityRegistry.INSTANCE;

        LootRarity rolled = Config.respectWorldTier()
            ? registry.getRandomItem(ctx)
            : uniformFromBounds(registry, ctx);

        if (rolled == null) {
            // Registry empty (data pack still loading) - nothing sensible to do.
            return null;
        }

        LootRarity clamped = clamp(registry, rolled);
        if (clamped == null) {
            // Bounds are unusable; fall back to the raw roll rather than dropping the item.
            LOGGER.warn("[ApotheosisExpansion] Could not resolve rarity bounds (min='{}', max='{}'); using rolled rarity {}.",
                Config.minRarity(), Config.maxRarity(), registry.getKey(rolled));
            return rolled;
        }
        return clamped;
    }

    /**
     * Ignores World Tier weighting and picks uniformly between the two configured bounds (inclusive).
     * Used when {@code respect_world_tier = false}.
     */
    @Nullable
    private static LootRarity uniformFromBounds(RarityRegistry registry, GenContext ctx) {
        List<LootRarity> sorted = sortedRarities(registry);
        if (sorted.isEmpty()) {
            return null;
        }
        int lo = indexOfKey(registry, sorted, Config.minRarity());
        int hi = indexOfKey(registry, sorted, Config.maxRarity());
        if (lo < 0 || hi < 0) {
            return registry.getRandomItem(ctx);
        }
        int from = Math.min(lo, hi);
        int to = Math.max(lo, hi);
        int pick = from + ctx.rand().nextInt(to - from + 1);
        return sorted.get(pick);
    }

    /**
     * Clamps the rolled rarity into the configured bounds using {@code sort_index} ordering.
     *
     * @return the clamped rarity, or {@code null} if either bound could not be resolved
     */
    @Nullable
    public static LootRarity clamp(RarityRegistry registry, LootRarity rolled) {
        List<LootRarity> sorted = sortedRarities(registry);
        if (sorted.isEmpty()) {
            return null;
        }

        int lo = indexOfKey(registry, sorted, Config.minRarity());
        int hi = indexOfKey(registry, sorted, Config.maxRarity());
        if (lo < 0 || hi < 0) {
            return null;
        }
        if (lo > hi) {
            // Swap so a reversed config still behaves sanely instead of clamping everything to one end.
            int tmp = lo;
            lo = hi;
            hi = tmp;
        }

        int idx = sorted.indexOf(rolled);
        if (idx < 0) {
            return rolled;
        }
        if (idx < lo) {
            return sorted.get(lo);
        }
        if (idx > hi) {
            return sorted.get(hi);
        }
        return rolled;
    }

    /**
     * Returns the registry contents ordered by {@code sort_index}. Apotheosis keeps such a list internally but only
     * exposes it as an unmodifiable view of a mutable list, so we sort a defensive copy here.
     */
    public static List<LootRarity> sortedRarities(RarityRegistry registry) {
        List<LootRarity> copy = new ArrayList<>(registry.getValues());
        copy.sort((a, b) -> Integer.compare(a.sortIndex(), b.sortIndex()));
        return copy;
    }

    /**
     * Resolves a configured rarity id to its index in the sorted list.
     *
     * @return the index, or {@code -1} if the id is unknown or not present in the registry
     */
    public static int indexOfKey(RarityRegistry registry, List<LootRarity> sorted, String id) {
        ResourceLocation key = parse(id);
        if (key == null) {
            return -1;
        }
        LootRarity value = registry.getValue(key);
        if (value == null) {
            return -1;
        }
        return sorted.indexOf(value);
    }

    @Nullable
    private static ResourceLocation parse(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return ResourceLocation.parse(id.trim());
        }
        catch (RuntimeException ex) {
            LOGGER.warn("[ApotheosisExpansion] Invalid rarity id '{}' in config.", id);
            return null;
        }
    }
}
