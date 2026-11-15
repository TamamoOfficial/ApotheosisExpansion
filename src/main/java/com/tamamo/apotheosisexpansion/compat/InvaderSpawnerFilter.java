package com.tamamo.apotheosisexpansion.compat;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.shadowsoffire.apotheosis.mobs.registries.InvaderRegistry;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import net.minecraft.resources.ResourceLocation;

/**
 * Decides whether an invader came from this mod, and re-rolls the boss spawner when it did.
 * <p>
 * <b>Identification is by registry namespace.</b> Every entry this mod adds is registered under
 * {@code apotheosis:apothic_invaders/apotheosis_expansion_*}, and Placebo keeps the resource location in its
 * {@code BiMap}. Using {@code InvaderRegistry.INSTANCE.getKey(invader)} is therefore exact: it does not rely on entity
 * type (the Thermal/Mek/Ars mobs are ordinary mobs that could also be added by another pack) and does not rely on
 * comparing against a hard-coded list of ids.
 * <p>
 * <b>Re-roll rather than null.</b> Returning {@code null} would make Apotheosis log "a boss spawner attempted to spawn
 * a boss ... but no bosses were available" and leave the block already destroyed by {@code setBlockAndUpdate(AIR)} -
 * a dungeon with no boss. So the pick is retried with a bounded number of attempts and the last non-filtered result is
 * kept. On the rare occasion all attempts land on this mod's entries, the original pick is returned, which is strictly
 * better than spawning nothing.
 */
public final class InvaderSpawnerFilter {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The {@code apothic_invaders} sub-folders this mod owns.
     * <p>
     * An explicit list rather than a name prefix: the folders are plain mod names ({@code thermal},
     * {@code mekanism}, ...) so there is no prefix to key off, and a list states the intent directly.
     * Adding a new compat folder to the data pack means adding it here too - the validator
     * {@code build/verify_spawner_filter.js} enforces that the two cannot drift apart.
     */
    private static final java.util.Set<String> OWN_FOLDERS = java.util.Set.of(
        "thermal",
        "mekanism",
        "ars_nouveau",
        "allthemodium",
        "vanilla_bosses");

    /** Bounded so a pack that somehow only ships this mod's entries cannot spin here. */
    private static final int MAX_ATTEMPTS = 8;

    private InvaderSpawnerFilter() {}

    /**
     * Returns a pick that is not one of this mod's entries, or the original pick if none could be found.
     *
     * @param picked the result of the original weighted pick
     * @return a replacement pick, never null when {@code picked} was not null
     */
    public static Invader replaceIfAdded(Invader picked, GenContext ctx) {
        if (picked == null || !isAddedByThisMod(picked)) {
            return picked;
        }

        InvaderRegistry registry = InvaderRegistry.INSTANCE;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Invader candidate = registry.getRandomItem(ctx);
            if (candidate == null) {
                break;
            }
            if (!isAddedByThisMod(candidate)) {
                return candidate;
            }
        }

        // Every attempt landed on this mod's entries. Keep the original rather than spawning nothing.
        LOGGER.debug("[ApotheosisExpansion] Boss spawner re-rolled {} times without escaping this mod's entries; keeping the original pick {}.",
            MAX_ATTEMPTS, registry.getKey(picked));
        return picked;
    }

    /**
     * True when a registry path sits inside one of this mod's invader folders.
     *
     * @param path registry path such as {@code apothic_invaders/thermal/basalz_peaks}
     */
    private static boolean isOwnPath(String path) {
        if (!path.startsWith("apothic_invaders/")) {
            return false;
        }
        String rest = path.substring("apothic_invaders/".length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return false; // a file directly under apothic_invaders/ is not ours
        }
        return OWN_FOLDERS.contains(rest.substring(0, slash));
    }

    /** The folders this mod contributes entries under. Exposed for validation. */
    public static java.util.Set<String> ownFolders() {
        return OWN_FOLDERS;
    }

    /** True when the invader's registry id belongs to a namespace path this mod owns. */
    public static boolean isAddedByThisMod(Invader invader) {
        ResourceLocation id = InvaderRegistry.INSTANCE.getKey(invader);
        // getKey returns null for an unregistered instance; treat that as "not ours" so we never block foreign entries.
        if (id == null || !"apotheosis".equals(id.getNamespace())) {
            return false;
        }
        return isOwnPath(id.getPath());
    }

    /**
     * Diagnostic helper: the registry ids this mod contributes to the invader registry right now.
     * <p>
     * Walks the registry keys directly instead of the values, so an entry whose file failed to bind is still reported
     * (it is a key this mod owns even while unbound). Useful when a pack reports unexpected boss spawner behaviour and
     * a log line is wanted.
     */
    public static Set<ResourceLocation> ownIds() {
        Set<ResourceLocation> out = new HashSet<>();
        for (ResourceLocation id : InvaderRegistry.INSTANCE.getKeys()) {
            if ("apotheosis".equals(id.getNamespace()) && isOwnPath(id.getPath())) {
                out.add(id);
            }
        }
        return out;
    }
}
