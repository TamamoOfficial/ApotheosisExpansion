package com.tamamo.apotheosisexpansion.mixin.spawn;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tamamo.apotheosisexpansion.spawn.LocalMobCapAccess;
import com.tamamo.apotheosisexpansion.spawn.MobCapScaler;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;

/**
 * Scales the level-wide monster cap inside {@code NaturalSpawner$SpawnState.canSpawnForCategory}.
 *
 * <p><b>The vanilla line being changed.</b>
 * <pre>
 *   int i = category.getMaxInstancesPerChunk() * this.spawnableChunkCount / NaturalSpawner.MAGIC_NUMBER;
 *   return this.mobCategoryCounts.getInt(category) &gt;= i ? false : this.localMobCapCalculator.canSpawn(category, pos);
 * </pre>
 * That single {@code getMaxInstancesPerChunk()} call is the 70 in {@code 70 * chunkCount / 289}. Together with
 * {@link MobCountsMixin} it accounts for the whole spawn budget: relaxing only one of the two leaves spawning stopped
 * at the vanilla number and the feature looks broken.
 *
 * <p><b>Why the chunk map is read from the calculator.</b> The chunk is a parameter here, but the <em>players near that
 * chunk</em> are not reachable from this class: the filtering happens inside the calculator's chunk map.
 * {@link LocalMobCapAccess} exposes it, so both halves of the budget are derived from exactly the same player set - the
 * set that also decides whether each individual player has room.
 *
 * <p><b>Why the handler takes three parameters.</b> A {@code @Redirect} on an instance method call receives the
 * receiver first, then the call's arguments (none here), then the enclosing method's parameters in declaration order -
 * here {@code (MobCategory category, ChunkPos pos)}.
 *
 * <p><b>Cost.</b> {@link MobCapScaler#multiplierFor} memoises per (level, tick, chunk), so the repeated calls from one
 * chunk pass - one per category, then one per candidate mob - reuse the same tier grouping instead of recomputing it.
 * The nearby-player scan itself still runs per call; see the note on the memo in {@link MobCapScaler}.
 *
 * <p><b>Why {@code remap = false}.</b> No refmap is shipped (this project builds with plain {@code javac}), so Mixin
 * has no mapping table and must be told these are already production names.
 */
@Mixin(NaturalSpawner.SpawnState.class)
public abstract class SpawnStateMixin {

    @Shadow
    @Final
    private LocalMobCapCalculator localMobCapCalculator;

    @Redirect(
        method = "canSpawnForCategory",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"),
        remap = false)
    private int apotheosisExpansion$scaledLevelCap(MobCategory receiver, MobCategory category, ChunkPos pos) {
        double multiplier = MobCapScaler.multiplierFor(
            ((LocalMobCapAccess) this.localMobCapCalculator).apotheosisExpansion$chunkMap(), pos);
        return MobCapScaler.scaleCap(receiver, receiver.getMaxInstancesPerChunk(), multiplier);
    }
}
