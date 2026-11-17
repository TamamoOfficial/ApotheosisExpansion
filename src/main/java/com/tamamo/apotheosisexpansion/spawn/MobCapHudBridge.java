package com.tamamo.apotheosisexpansion.spawn;

import javax.annotation.Nullable;

import com.tamamo.apotheosisexpansion.Config;
import com.tamamo.apotheosisexpansion.spawn.MobCapScaler;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;

/**
 * Supplies the real (scaled) monster cap to HUD mods that recompute the vanilla formula themselves.
 *
 * <h2>The problem this solves</h2>
 * MiniHUD and Servux never read the outcome of the spawn cap check. Each recomputes the same formula in its own class:
 * <pre>
 *   capacity = category.getMaxInstancesPerChunk() * spawnableChunks / 289
 * </pre>
 * {@link MobCapScaler} widens the cap by redirecting two call sites <em>inside</em> the spawn pipeline, so these
 * external recomputations keep reading the raw 70 and the HUD shows {@code x/70} while the world actually allows
 * {@code x/105}. The compat mixins redirect the same {@code getMaxInstancesPerChunk()} call inside <em>their</em> code,
 * and this class is what they ask for the replacement value.
 *
 * <h2>Why a per-dimension maximum rather than an exact per-chunk value</h2>
 * The HUD figures are level-wide to begin with - the divisor term is the union of every player's spawn chunks, a
 * single number per dimension. The residual {@code getMaxInstancesPerChunk()} call site also has nothing but a
 * {@code MobCategory} in scope: no chunk position, no player. A per-dimension maximum is therefore both the only
 * answer available and the one that shares the HUD's own granularity. It also matches what a player standing on a
 * shared platform experiences, since everyone near it gets the same bonus.
 *
 * <h2>Cold switch</h2>
 * Every entry point returns {@code null} - meaning "call the original code" - unless {@code sync_mob_cap_to_hud} is on.
 * With the option off the mod must be indistinguishable from an unpatched HUD, including the numbers it prints.
 */
public final class MobCapHudBridge {

    private MobCapHudBridge() {}

    /**
     * Largest multiplier currently in effect for this dimension.
     *
     * @return {@code null} when the HUD should keep vanilla behaviour, otherwise the multiplier to apply
     */
    @Nullable
    public static Double multiplierForLevel(@Nullable ServerLevel level) {
        if (level == null || !Config.syncMobCapToHud() || !Config.enableMobCapScaling()) {
            return null;
        }

        double best = 1.0D;
        for (ServerPlayer player : level.players()) {
            if (player == null || player.isSpectator()) {
                continue;
            }
            ChunkPos pos = player.chunkPosition();
            double mult = MobCapScaler.multiplierFor(level.getChunkSource().chunkMap, pos);
            if (mult > best) {
                best = mult;
            }
        }

        return best > 1.0D ? best : null;
    }

    /**
     * Largest multiplier across every dimension the server is ticking.
     * <p>
     * Used where the level is not a parameter of the injection point (Servux iterates the levels itself). Taking the
     * maximum is safe there because the value only ever changes a displayed number, and a HUD line reports one figure
     * anyway.
     */
    @Nullable
    public static Double multiplierForServer(@Nullable MinecraftServer server) {
        if (server == null || !Config.syncMobCapToHud() || !Config.enableMobCapScaling()) {
            return null;
        }

        double best = 1.0D;
        for (ServerLevel level : server.getAllLevels()) {
            Double mult = multiplierForLevel(level);
            if (mult != null && mult > best) {
                best = mult;
            }
        }

        return best > 1.0D ? best : null;
    }

    /**
     * Returns the per-chunk value a HUD should substitute for {@code category.getMaxInstancesPerChunk()}.
     *
     * @return {@code null} to let the HUD keep its own vanilla value
     */
    @Nullable
    public static Integer scaledCap(@Nullable MobCategory category, @Nullable Double multiplier) {
        if (category == null || multiplier == null || multiplier <= 1.0D) {
            return null;
        }
        if (category.isFriendly()) {
            // Symmetric with MobCapScaler.scaleCap: friendly categories keep their vanilla numbers everywhere.
            return null;
        }
        double value = category.getMaxInstancesPerChunk() * multiplier;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(value));
    }
}
