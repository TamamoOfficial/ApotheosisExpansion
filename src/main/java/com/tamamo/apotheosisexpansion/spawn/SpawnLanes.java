package com.tamamo.apotheosisexpansion.spawn;

import java.util.List;

import com.tamamo.apotheosisexpansion.Config;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Runs a second monster spawn pass on chunks whose area is dangerous enough.
 *
 * <h2>Cap versus throughput</h2>
 * {@link MobCapScaler} raises how many mobs may <em>exist</em>. This class raises how many spawn <em>attempts</em>
 * happen per tick. Both are needed because they fix different farms:
 * <ul>
 * <li>A farm that kills instantly never reaches the cap - extra budget does nothing for it, only extra attempts do.</li>
 * <li>A farm that lets mobs pile up sits at the cap - extra attempts are discarded, only extra budget helps.</li>
 * </ul>
 * Measured on a wandering kill tower (15-40 mobs alive, i.e. far below its cap): a second pass produced 1.97x the
 * mobs (95% CI 1.39-2.78) for roughly +0.21 ms of tick time.
 *
 * <h2>The gate: regional difficulty, not world tier</h2>
 * The threshold is vanilla's <b>Regional Difficulty</b> - the number behind the F3 "Local Difficulty" display, i.e.
 * {@code Level#getCurrentDifficultyAt(pos).getEffectiveDifficulty()}. It was chosen over Apotheosis' world tier for
 * three reasons:
 * <ol>
 * <li>It is a continuous 0.0-6.75 value, so a threshold can pick a meaningful "this area has been lived in" point.
 * A world tier is one of five arbitrary steps and says nothing about how busy or how long-running the area is.</li>
 * <li>It already scales mob strength, so gating on it keeps spawning consistent with what actually spawns.</li>
 * <li>It is per-position and vanilla-maintained: no extra bookkeeping, no state to persist, and it applies to modded
 * dimensions for free.</li>
 * </ol>
 *
 * <h2>Why the same {@code SpawnState} is reused</h2>
 * The second pass is given the state vanilla built for this tick, so the mobs the first pass created are already
 * counted. The level cap therefore still holds - this is a throughput change, not a silent cap increase - and the two
 * passes cannot over-spawn together.
 */
public final class SpawnLanes {

    /** Hard ceiling. Even a bug cannot turn this into an unbounded spawn loop. */
    public static final int MAX_LANES = 2;

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String LOG_TAG = "ApotheosisExpansion/SpawnLanes";

    /** Gates the per-chunk decision line. Same reasoning as {@code MobCapScaler}: loud, so off by default. */
    private static boolean DEBUG = false;

    /**
     * Running tallies, one line per second while {@link #DEBUG} is on.
     * <p>
     * A gate that silently never opens is indistinguishable from a feature that does not work, and in a farm that sits
     * at its population cap the extra passes produce no visible change even when they do run. These counters make
     * "did the second pass run" answerable independently of throughput.
     */
    private static long extraPassesRun;
    private static long extraPassesProducedMobs;
    private static long gateChecks;
    private static long gateOpened;

    /**
     * Depth of the {@link #runExtraPass} call currently on the stack.
     * <p>
     * The extra pass re-enters {@code NaturalSpawner.spawnForChunk}, which is the method the calling mixin is attached
     * to. Without this guard each nested call would start its own lane loop: infinite recursion, and a stack overflow
     * rather than a slowdown. A per-thread depth counter is the smallest thing that terminates.
     */
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    /** True while {@link #runExtraPass} is executing, so a nested pass cannot itself add a lane. */
    private static final ThreadLocal<Boolean> IN_LANE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private SpawnLanes() {}

    public static void setDebug(boolean debug) {
        DEBUG = debug;
    }

    /** Running tallies as one greppable line, for the debug log. */
    public static String stats() {
        return String.format(java.util.Locale.ROOT,
            "gate_checks=%d gate_opened=%d extra_passes=%d extra_mobs=%d",
            gateChecks, gateOpened, extraPassesRun, extraPassesProducedMobs);
    }

    /** Drops the counters; called on config reload so one run's numbers do not carry into the next configuration. */
    public static void resetStats() {
        gateChecks = 0L;
        gateOpened = 0L;
        extraPassesRun = 0L;
        extraPassesProducedMobs = 0L;
    }

    /**
     * Emits one line every {@code periodTicks} while debugging.
     * <p>
     * A gate that silently never opens is indistinguishable from a feature that does not work - and in a farm sitting
     * at its population cap, extra passes change nothing visible even when they run correctly. This makes
     * "did the second pass run, and did it produce anything" answerable independently of throughput.
     */
    public static void debugTick(long gameTime, int periodTicks) {
        if (!DEBUG || gameTime % periodTicks != 0L) {
            return;
        }
        LOGGER.info("[{}] {}", LOG_TAG, stats());
    }

    /**
     * How many spawn passes this chunk gets this tick: 1 (vanilla) or {@link #MAX_LANES}.
     *
     * <p><b>Why the level is a parameter.</b> {@code ChunkMap.level} is package-private, so it cannot be read from this
     * package. The mixin already has the {@code ServerLevel} in hand, and passing it in also makes the dependency
     * explicit rather than hiding it behind a reflective field read.
     *
     * @return 1 when the feature is off, the area is not dangerous enough, or anything is missing
     */
    public static int lanesFor(ServerLevel level, ChunkMap chunkMap, ChunkPos pos) {
        if (level == null || chunkMap == null || pos == null || !Config.enableSpawnLanes()) {
            return 1;
        }
        gateChecks++;

        double difficulty = level.getCurrentDifficultyAt(pos.getMiddleBlockPosition(
            // The Y coordinate does not influence regional difficulty (it is computed from time, chunk
            // inhabited-time and nearby player count), but the API needs one. 0 is inside every vanilla dimension.
            0)).getEffectiveDifficulty();

        if (difficulty < Config.spawnLanesMinRegionalDifficulty()) {
            return 1;
        }

        // Only real players by default: regional difficulty counts every ServerPlayer, including carpet's /player and
        // automation bots, so without this an empty area with a few parked bots would qualify.
        if (Config.spawnLanesRealPlayersOnly() && !hasRealPlayerNearby(chunkMap, pos)) {
            if (DEBUG) {
                LOGGER.info("[{}] chunk {} difficulty={} REJECTED: no real player nearby", LOG_TAG,
                    pos.toLong(), difficulty);
            }
            return 1;
        }

        gateOpened++; 
        if (DEBUG) {
            LOGGER.info("[{}] chunk {} difficulty={} -> {} lanes", LOG_TAG, pos.toLong(), difficulty, MAX_LANES);
        }
        return MAX_LANES;
    }

    /**
     * Whether at least one real (not fake) player is within spawn range of this chunk.
     * <p>
     * Reuses {@code getPlayersCloseForSpawning} so the range is exactly vanilla's 128 blocks, rather than a second
     * hard-coded constant that could drift from it.
     */
    private static boolean hasRealPlayerNearby(ChunkMap chunkMap, ChunkPos pos) {
        List<ServerPlayer> nearby = chunkMap.getPlayersCloseForSpawning(pos);
        for (ServerPlayer player : nearby) {
            if (!isFakePlayer(player)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Distinguishes a scripted stand-in from a person.
     * <p>
     * Deliberately duck-typed rather than referencing {@code net.neoforged.neoforge.common.util.FakePlayer}: carpet's
     * fake players are a plain {@code ServerPlayer} with a fake connection and would not match a class check, while a
     * {@code isFakePlayer()} call is answered correctly by NeoForge's own FakePlayer and costs nothing when the class
     * is absent. A connection that is null or not a {@code ServerGamePacketListenerImpl} covers everything else.
     */
    private static boolean isFakePlayer(ServerPlayer player) {
        try {
            java.lang.reflect.Method method = player.getClass().getMethod("isFakePlayer");
            Object result = method.invoke(player);
            if (result instanceof Boolean fake && fake) {
                return true;
            }
        }
        catch (ReflectiveOperationException |
               RuntimeException ignored) {
            // No such method, or it threw: fall through to the connection check.
        }
        return player.connection == null
            || !(player.connection instanceof net.minecraft.server.network.ServerGamePacketListenerImpl);
    }

    /**
     * Runs the extra passes for one chunk, using the state vanilla just used.
     *
     * @param vanillaPasses how many passes vanilla already ran this tick (always 1 here)
     */
    public static void runExtraPass(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk,
                                    net.minecraft.world.level.NaturalSpawner.SpawnState state,
                                    int vanillaPasses, boolean forced) {
        if (IN_LANE.get()) {
            return;
        }

        int lanes = lanesFor(level, level.getChunkSource().chunkMap, chunk.getPos());
        int extra = lanes - vanillaPasses;

        for (int lane = 0; lane < extra; lane++) {
            if (DEPTH.get() > 0) {
                break;
            }
            IN_LANE.set(Boolean.TRUE);
            DEPTH.set(DEPTH.get() + 1);
            long before = state.getMobCategoryCounts().getInt(net.minecraft.world.entity.MobCategory.MONSTER);
            try {
                // Enemies only: repeating the friendly pass would double animals too, which no farm asks for and
                // which would make the two lanes incomparable with the cap options.
                net.minecraft.world.level.NaturalSpawner.spawnForChunk(level, chunk, state, false, true, forced);
                extraPassesRun++;
                extraPassesProducedMobs += Math.max(0, state.getMobCategoryCounts().getInt(
                    net.minecraft.world.entity.MobCategory.MONSTER) - before);
            }
            catch (RuntimeException ex) {
                // A spawn pass that throws must not take the whole tick down, and must not leave the guards latched.
                LOGGER.error("[{}] extra spawn pass failed for chunk {}", LOG_TAG, chunk.getPos().toLong(), ex);
            }
            finally {
                DEPTH.set(DEPTH.get() - 1);
                IN_LANE.set(Boolean.FALSE);
            }
        }
    }
}
