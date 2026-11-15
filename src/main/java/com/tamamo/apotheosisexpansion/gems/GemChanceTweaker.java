package com.tamamo.apotheosisexpansion.gems;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.logging.LogUtils;
import dev.shadowsoffire.apotheosis.loot.modifiers.GemLootModifier;
import dev.shadowsoffire.apotheosis.loot.modifiers.GemLootModifier.GemTableEntry;
import net.neoforged.neoforge.common.NeoForgeEventHandler;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifierManager;
import org.slf4j.Logger;

/**
 * Scales the drop chance of Apotheosis' gem loot table entries.
 * <p>
 * {@link GemLootModifier} keeps its entries in a {@code protected final List}, loaded from datapack JSON, with no
 * setter. The list is therefore replaced reflectively with a freshly built one, which is safe because nothing in
 * Apotheosis caches the old list identity.
 * <p>
 * Two robustness details, both learned from how this behaves across data pack reloads:
 * <ul>
 * <li>The original entries are cached per modifier <em>by identity</em>, so repeated calls rescale from the original
 * rather than compounding. Applying {@code x2} five times still means {@code x2}.</li>
 * <li>{@code LootModifierManager} is resolved through {@code NeoForgeEventHandler.getLootModifierManager()}, a
 * package-private static. The method is cached once; if it disappears the feature disables itself instead of
 * crashing.</li>
 * </ul>
 */
public final class GemChanceTweaker {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Field ENTRIES = resolveEntriesField();
    private static final Method GET_MANAGER = resolveManagerAccessor();

    private static final Map<GemLootModifier, List<GemTableEntry>> PRISTINE = new IdentityHashMap<>();
    private static final Map<GemLootModifier, List<GemTableEntry>> LAST_SCALED = new IdentityHashMap<>();

    private GemChanceTweaker() {}

    private static Field resolveEntriesField() {
        try {
            Field f = GemLootModifier.class.getDeclaredField("entries");
            f.setAccessible(true);
            return f;
        }
        catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.error("[ApotheosisExpansion] Could not access GemLootModifier.entries; drop chance tweaks are disabled.", ex);
            return null;
        }
    }

    private static Method resolveManagerAccessor() {
        try {
            Method m = NeoForgeEventHandler.class.getDeclaredMethod("getLootModifierManager");
            m.setAccessible(true);
            return m;
        }
        catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.error("[ApotheosisExpansion] Could not access LootModifierManager; drop chance tweaks are disabled.", ex);
            return null;
        }
    }

    /** True when drop chance tweaking is usable with the installed NeoForge / Apotheosis. */
    public static boolean isAvailable() {
        return ENTRIES != null && GET_MANAGER != null;
    }

    /**
     * Scales every gem loot modifier's entry chances by the given multiplier.
     *
     * @param multiplier {@code 1.0} leaves everything untouched, {@code 0.0} disables gem drops from these tables
     */
    public static void apply(double multiplier) {
        if (!isAvailable()) {
            return;
        }
        double mult = Math.max(0.0D, multiplier);

        LootModifierManager manager = manager();
        if (manager == null) {
            return;
        }

        int mods = 0;
        for (IGlobalLootModifier mod : manager.getAllLootMods()) {
            if (!(mod instanceof GemLootModifier gemMod)) {
                continue;
            }

            List<GemTableEntry> original;
            if (LAST_SCALED.containsKey(gemMod)) {
                original = PRISTINE.get(gemMod);
            }
            else {
                original = new ArrayList<>(entriesOf(gemMod));
                PRISTINE.put(gemMod, original);
                LAST_SCALED.put(gemMod, original);
            }
            if (original == null) {
                continue;
            }

            List<GemTableEntry> scaled = new ArrayList<>(original.size());
            for (GemTableEntry e : original) {
                float chance = (float) Math.max(0.0D, Math.min(1.0D, e.chance() * mult));
                scaled.add(new GemTableEntry(e.pattern(), chance, e.gems(), e.purities()));
            }

            try {
                ENTRIES.set(gemMod, scaled);
                mods++;
            }
            catch (IllegalAccessException ex) {
                LOGGER.error("[ApotheosisExpansion] Failed to scale chance of a GemLootModifier", ex);
            }
        }

        LOGGER.info("[ApotheosisExpansion] Gem drop chance tweak applied: {} modifier(s) scaled by x{}", mods, mult);
    }

    /** Restores every touched modifier to its original entry chances. */
    public static void restore() {
        if (!isAvailable()) {
            return;
        }
        int restored = 0;
        for (Map.Entry<GemLootModifier, List<GemTableEntry>> e : PRISTINE.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            try {
                ENTRIES.set(e.getKey(), new ArrayList<>(e.getValue()));
                restored++;
            }
            catch (IllegalAccessException ex) {
                LOGGER.error("[ApotheosisExpansion] Failed to restore a GemLootModifier", ex);
            }
        }
        if (restored > 0) {
            LOGGER.info("[ApotheosisExpansion] Restored original drop chances for {} modifier(s)", restored);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<GemTableEntry> entriesOf(GemLootModifier m) {
        try {
            return (List<GemTableEntry>) ENTRIES.get(m);
        }
        catch (IllegalAccessException ex) {
            LOGGER.error("[ApotheosisExpansion] Failed to read GemLootModifier.entries", ex);
            return null;
        }
    }

    private static LootModifierManager manager() {
        try {
            return (LootModifierManager) GET_MANAGER.invoke(null);
        }
        catch (ReflectiveOperationException | RuntimeException ex) {
            // The manager only exists once a server is running; this is normal during early startup.
            LOGGER.debug("[ApotheosisExpansion] LootModifierManager not available yet: {}", ex.getMessage());
            return null;
        }
    }
}
