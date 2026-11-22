package com.tamamo.apotheosisexpansion.gemcompendium;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.GemBonus;
import dev.shadowsoffire.apotheosis.tiers.Constraints;
import dev.shadowsoffire.apotheosis.tiers.TieredWeights;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.resources.ResourceLocation;

/**
 * The facts the gem compendium pages are built from: where a gem drops, at what world tiers, and what each purity
 * actually gives.
 *
 * <h2>Where the numbers come from</h2>
 * Everything here is read from the <em>live</em> {@link GemRegistry} rather than from the shipped JSON. That is the
 * whole point of the feature: the registry already reflects the loaded data pack, every other mod's gems, and this
 * mod's own weight tweaks, so a page can never disagree with what actually drops.
 *
 * <h2>One page per gem</h2>
 * A gem's whole bonus table fits one page because the purities share each line: a bonus row lists its value at
 * every purity the gem can exist at, separated by "/", with a legend line giving the purity order. Splitting per
 * purity instead produced a page per (gem, purity) - dozens of near-identical entries in the category list - which
 * is exactly what a compendium should not do.
 *
 * <h2>Why {@code bonusesByPurity} is not precomputed</h2>
 * {@link GemBonus#supports(Purity)} is what Apotheosis itself consults before printing a bonus, and a bonus may
 * cover only some purities (a sparse {@code values} map). The page asks it per purity while building each row, so
 * the line shows exactly the values Apotheosis would show in that gem's own tooltip.
 */
public final class GemCompendiumModel {

    /** One gem's drop rules and bonus table: the content of one viewer page. */
    public record Entry(
        ResourceLocation id,
        Gem gem,
        /** Dimensions the gem is restricted to; empty means every dimension. */
        List<ResourceLocation> dimensions,
        /** World tiers it can be rolled at, ascending. Empty means the gem carries no tier weights at all. */
        List<WorldTier> tiers,
        /** Sorted, de-duplicated socket-target keys ("melee_weapon", ...), for the "Fits In" line. */
        List<String> gemClasses,
        /** Lowest purity the gem can exist at. */
        Purity minPurity,
        boolean unique) {

        /** Whether the gem is gated to specific dimensions at all. */
        public boolean dimensionRestricted() {
            return !this.dimensions.isEmpty();
        }
    }

    private GemCompendiumModel() {}

    /**
     * Every page to publish - one per gem - sorted by gem id.
     * <p>
     * Returns an empty list before the first data reload - the registry is empty then, and the callers simply
     * publish nothing; both viewers re-run their registration after every reload, so the pages appear as soon as
     * gems exist. A gem with no bonuses at all gets no page: there would be nothing on it but the drop block.
     */
    public static List<Entry> snapshotEntries() {
        List<Entry> entries = new ArrayList<>();
        for (Gem gem : GemRegistry.INSTANCE.getValues()) {
            ResourceLocation id = GemRegistry.INSTANCE.getKey(gem);
            if (id == null || gem.getBonuses().isEmpty()) {
                continue;
            }
            entries.add(describe(id, gem));
        }
        entries.sort(Comparator.comparing(e -> e.id().toString()));
        return entries;
    }

    /** Builds one entry. Public so the static verifier can drive it with a hand-built gem. */
    public static Entry describe(ResourceLocation id, Gem gem) {
        Constraints constraints = gem.constraints();
        // The constraint stores dimension *keys*; the page only needs their ids, which is what a data pack writes
        // and what a translation key is derived from.
        List<ResourceLocation> dims = constraints.dimensions().stream()
            .map(key -> key.location())
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .toList();
        List<WorldTier> tiers = tiersOf(gem.weights());
        List<String> classes = gemClasses(gem);
        return new Entry(id, gem, dims, tiers, classes, gem.getMinPurity(), gem.isUnique());
    }

    /**
     * The tiers a gem can roll at, ascending.
     * <p>
     * A weight of zero is dropped: a tier listing a gem at weight 0 never produces it, and showing that tier would
     * claim a drop the game never performs. The codec already normalises the weights map to "every tier, or exactly
     * the listed ones", so what remains here is exactly what the drop roll consults.
     */
    private static List<WorldTier> tiersOf(TieredWeights weights) {
        Set<WorldTier> tiers = new TreeSet<>(Comparator.comparingInt(WorldTier::ordinal));
        if (weights == null) {
            return List.of();
        }
        weights.weights().forEach((tier, weight) -> {
            if (tier != null && weight != null && weight.weight() > 0) {
                tiers.add(tier);
            }
        });
        return List.copyOf(tiers);
    }

    /** The socket-target keys this gem declares, sorted and de-duplicated. */
    private static List<String> gemClasses(Gem gem) {
        Set<String> keys = new LinkedHashSet<>();
        for (GemBonus bonus : gem.getBonuses()) {
            if (bonus == null || bonus.getGemClass() == null) {
                continue;
            }
            String key = bonus.getGemClass().key();
            if (key != null && !key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys.stream().sorted().toList();
    }
}
