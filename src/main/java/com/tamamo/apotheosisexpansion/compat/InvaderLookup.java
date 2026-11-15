package com.tamamo.apotheosisexpansion.compat;

import java.util.Optional;

import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.mobs.registries.InvaderRegistry;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared lookups for the hand-written boss conversions.
 *
 * <p>Three converters need the same two operations - resolve an invader entry by id, and pick the
 * strongest rarity that entry actually carries stats for. They are kept here so the behaviour cannot
 * drift between {@link VanillaBossHooks} and {@link PiglichGearHook}: a fix to the selection rule
 * applies to every boss at once.
 */
public final class InvaderLookup {

    private InvaderLookup() {}

    /**
     * Resolves an invader entry.
     * <p>
     * Absent is normal, not an error: the data pack may not have been reloaded yet, or an optional
     * mod's entries may be gated out.
     */
    public static Optional<Invader> find(ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        Invader invader = InvaderRegistry.INSTANCE.getValue(id);
        return invader == null ? Optional.empty() : Optional.of(invader);
    }

    /**
     * Picks the highest-sorting rarity the entry carries stats for.
     * <p>
     * {@code Invader#initBoss} looks the rarity up in the entry's own {@code stats} map, so a rarity
     * the entry does not define cannot be used. Choosing the maximum keeps these fights at the top of
     * their available curve without needing per-rarity weights in the data files.
     *
     * @return the chosen rarity, or null when the entry defines none
     */
    public static LootRarity bestRarity(Invader invader) {
        if (invader == null || invader.stats().isEmpty()) {
            return null;
        }
        LootRarity best = null;
        for (LootRarity rarity : invader.stats().keySet()) {
            if (best == null || rarity.sortIndex() > best.sortIndex()) {
                best = rarity;
            }
        }
        return best;
    }
}
