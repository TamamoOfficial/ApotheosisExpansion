package com.tamamo.apotheosisexpansion.gems;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.logging.LogUtils;
import com.tamamo.apotheosisexpansion.Config;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.slf4j.Logger;

/**
 * Applies the gem drop tweaks at the two moments they can change.
 * <p>
 * Everything is driven by the single mod config file; there is no second config file.
 * <p>
 * <b>Why {@link OnDatapackSyncEvent}.</b> Both targets are data pack content: {@code GemRegistry} is a dynamic registry
 * reloaded from {@code data/<ns>/gems/}, and {@code GemLootModifier} instances are rebuilt from
 * {@code data/<ns>/loot_modifiers/} on every reload. Any multiplier written earlier points at objects that no longer
 * exist, so the tweaks must be re-applied after each reload — and this event fires exactly then (and again per player
 * join, where re-applying is harmless because the helpers are idempotent).
 * <p>
 * <b>Why not a reload listener.</b> A {@code SimpleJsonResourceReloadListener} would need to be ordered after the gem
 * registry and the loot modifier manager, which are registered by other mods. {@code OnDatapackSyncEvent} already runs
 * after every reload listener has finished, so ordering is guaranteed without extra wiring.
 * <p>
 * <b>Why the config multiplier alone is not enough.</b> Changing {@code gem_drop_chance_multiplier} in the config
 * screen only refreshes a cached value; the already-scaled loot modifiers keep the old chance. The mod therefore also
 * re-runs {@link #applyAll()} whenever the config is reloaded, which is what makes the toggle hot-reloadable.
 */
public final class GemTweakApplier {

    private static final Logger LOGGER = LogUtils.getLogger();

    private GemTweakApplier() {}

    /** Hooked to {@link OnDatapackSyncEvent}; re-applies after every data pack reload and on player join. */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        try {
            applyAll();
        }
        catch (Throwable t) {
            // A gem tweak failure must never break world loading or a player joining.
            LOGGER.error("[ApotheosisExpansion] Failed to apply gem drop tweaks", t);
        }
    }

    /**
     * Applies (or reverts) both tweaks according to the current config.
     * <p>
     * Safe to call repeatedly; every helper it delegates to caches its pristine values and rescales from those.
     */
    public static void applyAll() {
        if (!Config.enableGemTweaks()) {
            // Restore rather than just skipping: a toggle that is switched off must put Apotheosis back the way it was
            // instead of leaving the previously scaled values in place.
            GemWeightTweaker.restore();
            GemChanceTweaker.restore();
            return;
        }

        // Top up the per-gem list with any gem the player has never seen before. This can only happen once a world is
        // running, since the gem registry is populated by data packs.
        List<String> missing = missingGemIds(currentGemIds());
        if (Config.appendGemWeightEntries(missing)) {
            LOGGER.info("[ApotheosisExpansion] Added {} newly discovered gem id(s) to gem_weight_multipliers", missing.size());
        }

        GemWeightTweaker.apply(Config.gemWeightMultipliers());
        // Only the drop chance is scaled here; the per-gem weight multipliers are handled above, which keeps the
        // meaning of "drop chance multiplier" identical to the original mod.
        GemChanceTweaker.apply(Config.gemDropChanceMultiplier());
    }

    /**
     * Returns the canonical ids that have no entry at all in {@code gem_weight_multipliers}.
     * <p>
     * A hand-written short form ({@code core/ballast} or {@code ballast}) counts as covering its gem. That matters:
     * {@link GemWeightTweaker} resolves the full id first, so appending {@code apotheosis:core/ballast=1.0} next to a
     * user's {@code ballast=3.0} would silently override the user's value with 1.0. Entries are therefore matched the
     * same way the tweaker resolves them.
     */
    static List<String> missingGemIds(List<String> canonicalIds) {
        Set<String> configured = new HashSet<>();
        for (String entry : Config.GEM_WEIGHT_MULTIPLIERS.get()) {
            if (entry == null) {
                continue;
            }
            int eq = entry.lastIndexOf('=');
            if (eq > 0) {
                configured.add(entry.substring(0, eq).trim());
            }
        }

        List<String> missing = new ArrayList<>();
        for (String id : canonicalIds) {
            ResourceLocation loc = ResourceLocation.tryParse(id);
            if (loc == null) {
                continue;
            }
            String path = loc.getPath();
            int slash = path.lastIndexOf('/');
            String shortName = slash >= 0 ? path.substring(slash + 1) : path;
            // Mirrors GemWeightTweaker.resolveMultiplier precedence: full id, namespaced path, bare name.
            if (configured.contains(id) || configured.contains(path) || configured.contains(shortName)) {
                continue;
            }
            missing.add(id);
        }
        return missing;
    }

    /** Sorted ids of every registered gem, used to seed / update {@code gem_weight_multipliers}. */
    private static List<String> currentGemIds() {
        return GemRegistry.INSTANCE.getKeys().stream()
            .map(ResourceLocation::toString)
            .sorted()
            .toList();
    }
}
