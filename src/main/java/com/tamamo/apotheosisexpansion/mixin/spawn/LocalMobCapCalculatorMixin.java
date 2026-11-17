package com.tamamo.apotheosisexpansion.mixin.spawn;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tamamo.apotheosisexpansion.spawn.LocalMobCapAccess;
import com.tamamo.apotheosisexpansion.spawn.MobCapScaler;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;

/**
 * Publishes the per-chunk cap multiplier for the duration of one vanilla per-player cap check.
 *
 * <p><b>What vanilla does here.</b> {@code canSpawn} walks every non-spectator player within 128 blocks of the chunk
 * and allows the spawn as soon as <em>one</em> of them is below the category's per-player cap. The actual comparison
 * lives in the nested {@code MobCounts.canSpawn}, which reads {@code MobCategory.getMaxInstancesPerChunk()} directly.
 *
 * <p><b>Why the multiplier is passed through a thread-local.</b> The multiplier is a property of the <em>chunk</em>,
 * but the nested class only receives a {@code MobCategory}: it has no argument identifying the chunk, and there is no
 * field linking it back. Computing it here - where both the chunk and the player list are available - and reading it
 * back in {@link MobCountsMixin} keeps each class responsible for what it can actually see.
 *
 * <p>The push/pop pair is exception-safe: the pop is a {@code RETURN} injection, which the compiler emits at every
 * {@code ireturn} in the method, and Mixin runs {@code RETURN} injections on all of them.
 */
@Mixin(LocalMobCapCalculator.class)
public abstract class LocalMobCapCalculatorMixin implements LocalMobCapAccess {

    @Shadow
    @Final
    private ChunkMap chunkMap;

    @Override
    public ChunkMap apotheosisExpansion$chunkMap() {
        return this.chunkMap;
    }

    /**
     * Computes and publishes the multiplier before vanilla starts walking the player list.
     * <p>
     * A multiplier of 1 - feature off, lone player, or all nearby players in different tiers - leaves the vanilla loop
     * completely untouched, so default behaviour is byte-for-byte identical.
     */
    @Inject(method = "canSpawn", at = @At("HEAD"))
    private void apotheosisExpansion$pushCapMultiplier(
        MobCategory category,
        ChunkPos pos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        MobCapScaler.pushMultiplier(MobCapScaler.multiplierFor(this.chunkMap, pos));
    }

    /** Restores the previous multiplier as the vanilla loop exits, whichever return it takes. */
    @Inject(method = "canSpawn", at = @At("RETURN"))
    private void apotheosisExpansion$popCapMultiplier(
        MobCategory category,
        ChunkPos pos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        MobCapScaler.popMultiplier();
    }
}
