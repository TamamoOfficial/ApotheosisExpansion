package com.tamamo.apotheosisexpansion.spawn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.tamamo.apotheosisexpansion.Config;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;

/**
 * Widens the vanilla monster cap when several players of the same world tier share one spawn area.
 *
 * <h2>The two caps vanilla applies</h2>
 * The spawn budget is enforced twice, at two different scales, and both must be relaxed or neither shows any effect:
 * <ol>
 * <li><b>Per player</b> - {@code LocalMobCapCalculator.canSpawn} asks every non-spectator player within 128 blocks of
 * the chunk whether they still have room under {@code maxInstancesPerChunk} (70 for {@code MONSTER}). Spawning is
 * allowed when <em>any</em> of them has room, so one player is worth 70.</li>
 * <li><b>Per level</b> - {@code NaturalSpawner$SpawnState.canSpawnForCategory} counts every mob in the level and
 * compares it against {@code 70 * spawnableChunkCount / 289}. That chunk count is the <em>union</em> of all players'
 * 17x17 spawn boxes, which is why two players standing together still get roughly one player's worth of monsters:
 * their boxes overlap almost completely.</li>
 * </ol>
 *
 * <h2>Formula</h2>
 * <pre>
 *   cap = baseCap * (1 + bonus * (n - 1))          n = nearby players sharing the majority world tier
 * </pre>
 * With {@code bonus = 0.5} a cluster of one / two / three players gets 70 / 105 / 140, i.e. {@code 70 + 35 * extra} -
 * the shape the option is named after. {@code bonus = 1.0} reaches 140 with only two players.
 *
 * <h2>Why the players must share a world tier</h2>
 * Apotheosis derives mob strength from the <em>nearest</em> player's world tier, so the highest-tier player in a
 * cluster already decides what spawns around that platform. Counting only the majority tier keeps the two systems
 * consistent: a pair of same-tier players is rewarded, a mixed pair is not, and nobody can inflate the budget by
 * stacking several tiers on one spot.
 *
 * <h2>Why the largest group and not the sum</h2>
 * The level-wide cap is a single global number, so it has to be derived from one concrete spawn context. The largest
 * single-tier group is that context: it is the group whose tier actually decides which invaders and elites appear.
 */
public final class MobCapScaler {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String LOG_TAG = "ApotheosisExpansion/MobCap";

    /**
     * Gates the per-chunk diagnostic line. Off by default: the line fires once per chunk per tick, which is far too
     * loud for normal play but is the only practical way to see which tier each nearby player actually resolves to.
     */
    private static boolean DEBUG_MULTIPLIERS = false;

    public static void setDebug(boolean debug) {
        DEBUG_MULTIPLIERS = debug;
    }

    private MobCapScaler() {}

    /**
     * Multipliers of the per-player cap checks currently on the stack, innermost last.
     * <p>
     * A stack rather than a single value because the per-player check ({@code LocalMobCapCalculator.canSpawn}) and the
     * nested comparison it drives ({@code MobCounts.canSpawn}) are separate classes, and the nested one has no way to
     * reach the chunk it is being asked about. Re-entrancy is possible in principle (a mob spawned from inside a spawn
     * callback), so the outer frame's value has to survive an inner push/pop pair.
     */
    private static final ThreadLocal<Deque<Double>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * One cached lookup. The level and tick are part of the key because every level shares the same game time, so a
     * chunk position alone would let one dimension read another's answer.
     */
    /**
     * Memo key: which level, which tick, which chunk, and <b>which generation of the config</b>.
     * <p>
     * The generation is what makes a config change take effect immediately. Clearing the map on reload is necessary
     * but not sufficient: a chunk can be queried again within the same tick, and a value written earlier in that tick
     * would be structurally valid and get served. Folding a counter into the key makes a stale entry impossible to
     * match instead of relying on clear ordering.
     */
    private record CacheKey(int level, long tick, long chunk, int generation) {}

    /**
     * Per-tick memo of the resolved multiplier.
     * <p>
     * It saves the <em>tier grouping</em> (a map plus an attachment read per player), not the nearby-player scan:
     * the scan still runs on every call because a {@link ServerPlayer} is the only handle this class has on a level
     * and therefore on the game time used as the memo key. That is acceptable because
     * {@code ChunkMap.getPlayersCloseForSpawning} bails out on the cheap {@code hasPlayersNearby} set lookup for any
     * chunk that has no players in range, and returns early when fewer than two are near - so the scan is only ever
     * paid on the chunks this feature is meant to affect.
     */
    private static final Map<CacheKey, Double> CACHE = new HashMap<>();

    /**
     * Bumped whenever the memo is dropped, and folded into every {@link CacheKey}.
     * <p>
     * This is what makes a config change take effect on the very next spawn pass instead of whenever the tick key
     * happens to roll over. Clearing the map alone would leave a window: a chunk can be queried twice in one tick, and
     * an entry written earlier in that same tick would still look valid after a clear that lost the race. A generation
     * counter makes an old entry unmatchable rather than merely absent.
     */
    private static int GENERATION = 0;

    /** Hard stop so a large server cannot grow the memo without bound before the tick key rolls over. */
    private static final int MAX_CACHE_ENTRIES = 8192;

    /**
     * Multiplier for one chunk, based on the players that can actually reach it.
     *
     * @return 1 when the feature is off, nobody else is near, or the nearby players all sit in different tiers
     */
    public static double multiplierFor(@Nullable ChunkMap chunkMap, @Nullable ChunkPos pos) {
        if (chunkMap == null || pos == null || !Config.enableMobCapScaling()) {
            return 1.0D;
        }

        List<ServerPlayer> nearby = chunkMap.getPlayersCloseForSpawning(pos);
        if (nearby.size() <= 1) {
            return 1.0D;
        }

        ServerPlayer sample = nearby.get(0);
        CacheKey key = new CacheKey(
            System.identityHashCode(sample.serverLevel()),
            sample.level().getGameTime(),
            pos.toLong(),
            GENERATION);

        Double cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        // Two counting rules. Which one applies is a config switch, so it is resolved per call rather than baked in -
        // flipping it takes effect on the next chunk pass, no relog and no restart.
        int group;
        String groupLabel;
        if (Config.mobCapCountAllTiers()) {
            group = Math.max(1, nearby.size());
            groupLabel = "all nearby players";
        }
        else {
            group = largestSameTierGroup(nearby);
            groupLabel = "largest same-tier group";
        }

        if (DEBUG_MULTIPLIERS) {
            StringBuilder sb = new StringBuilder();
            for (ServerPlayer listed : nearby) {
                sb.append(listed.getGameProfile().getName())
                  .append('=').append(WorldTier.getTier(listed)).append(' ');
            }
            LOGGER.info("[{}] chunk {} near [{}] -> {} {} ({}) -> x{}",
                LOG_TAG, pos.toLong(), sb.toString().trim(), groupLabel, group,
                describeGroup(nearby, group), multiplier(group));
        }

        double mult = multiplier(group);
        if (CACHE.size() > MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(key, mult);
        return mult;
    }

    /**
     * Applies the configured bonus for a group of {@code n} players that share a world tier.
     * <p>
     * The value is kept fractional on purpose. With a 0.5 bonus two players give exactly 1.5, and 1.5 x 70 = 105 is
     * one of the documented results; rounding the multiplier to an integer first would turn that into x2 = 140 and
     * make the option overshoot its own documentation.
     *
     * @return 1.0 when there is no group to speak of, or the feature is off
     */
    public static double multiplier(int n) {
        if (n <= 1 || !Config.enableMobCapScaling()) {
            return 1.0D;
        }

        double bonus = Config.mobCapBonusPerPlayer();
        if (bonus <= 0.0D) {
            return 1.0D;
        }

        int capped = Math.min(n, Math.max(2, Config.mobCapMaxPlayers()));
        return Math.max(1.0D, 1.0D + bonus * (capped - 1));
    }

    /**
     * Scales a cap by the multiplier.
     * <p>
     * Only hostile categories are scaled: friendly categories have their own small caps that nobody asked to change,
     * and widening those would let passive farms eat into the hostile budget through unrelated bookkeeping.
     */
    public static int scaleCap(@Nullable MobCategory category, int baseCap, double multiplier) {
        if (baseCap <= 0 || multiplier <= 1.0D || category == null || category.isFriendly()) {
            return baseCap;
        }
        // Rounded, not truncated: the level-wide term floors via integer division further down, so truncating here
        // too would let the two roundings compound into an off-by-a-few budget on large chunk counts.
        double scaled = baseCap * multiplier;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(scaled));
    }

    /**
     * Size of the largest group of players that share a world tier.
     * <p>
     * Returns 1 for an empty or single-element list so callers can treat "no bonus" uniformly.
     */
    public static int largestSameTierGroup(@Nullable List<ServerPlayer> players) {
        if (players == null || players.size() <= 1) {
            return 1;
        }

        Map<WorldTier, Integer> perTier = new HashMap<>();
        for (ServerPlayer player : players) {
            WorldTier tier;
            try {
                tier = WorldTier.getTier(player);
            }
            catch (RuntimeException ex) {
                // The world-tier attachment is absent until Apotheosis has set it at least once. Skip such a player
                // rather than letting an exception escape into the spawn path.
                continue;
            }
            if (tier != null) {
                perTier.merge(tier, 1, Integer::sum);
            }
        }

        int best = 1;
        for (int count : perTier.values()) {
            best = Math.max(best, count);
        }
        return best;
    }

    /** Enters a per-player cap check whose multiplier is {@code mult}. Must be paired with {@link #popMultiplier()}. */
    public static void pushMultiplier(double mult) {
        STACK.get().push(mult);
    }

    /** Leaves the innermost per-player cap check, restoring whatever was on the stack before it. */
    public static void popMultiplier() {
        Deque<Double> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /** Multiplier of the innermost per-player cap check in progress, or 1.0 when none is. */
    public static double currentMultiplier() {
        Deque<Double> stack = STACK.get();
        return stack.isEmpty() ? 1.0D : stack.peek();
    }

    /**
     * Drops the memo and invalidates it for good.
     * <p>
     * Bumping {@link #GENERATION} is the part that matters: it makes every existing key unmatchable, so a value
     * computed under the old config cannot be served even if the clear somehow raced with a read.
     */
    public static void clearCache() {
        GENERATION++;
        CACHE.clear();
    }

    /**
     * Names the players that make up the winning group, so the diagnostic line says <em>who</em> supplied the bonus and
     * not just how many did.
     * <p>
     * Only meaningful while {@link #DEBUG_MULTIPLIERS} is on; it walks the list a second time, which is fine for a
     * switch that exists to be turned on briefly.
     */
    private static String describeGroup(List<ServerPlayer> nearby, int groupSize) {
        if (groupSize <= 1) {
            return "no shared tier";
        }

        // All-tiers mode: every nearby player contributes, so list them all.
        if (Config.mobCapCountAllTiers()) {
            StringBuilder sb = new StringBuilder("all: ");
            for (ServerPlayer player : nearby) {
                sb.append(player.getGameProfile().getName()).append(' ');
            }
            return sb.toString().trim();
        }

        // Same-tier mode: find the tier that reaches groupSize, then list exactly the players in it.
        Map<WorldTier, Integer> perTier = new HashMap<>();
        for (ServerPlayer player : nearby) {
            perTier.merge(WorldTier.getTier(player), 1, Integer::sum);
        }
        WorldTier winning = null;
        for (Map.Entry<WorldTier, Integer> entry : perTier.entrySet()) {
            if (entry.getValue() == groupSize) {
                winning = entry.getKey();
                break;
            }
        }
        if (winning == null) {
            return "?";
        }

        StringBuilder sb = new StringBuilder("tier ").append(winning).append(": ");
        for (ServerPlayer player : nearby) {
            if (WorldTier.getTier(player) == winning) {
                sb.append(player.getGameProfile().getName()).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
