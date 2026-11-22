package com.tamamo.apotheosisexpansion.mixin.spawn;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tamamo.apotheosisexpansion.spawn.SpawnLanes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Runs a second spawn pass on chunks that qualify - the "two spawn lanes" feature.
 *
 * <h2>Why the hook is at the return of {@code spawnForChunk}</h2>
 * The extra pass needs three things that only exist inside vanilla's chunk loop: the {@code SpawnState} built for this
 * tick, the {@code LevelChunk} being processed, and the {@code forced} flag. Hooking the end of the real call means
 * the second pass is byte-for-byte the same work as the first, with the same state - which is exactly what makes this a
 * throughput change rather than a cap change. Reconstructing the state from a tick hook would build a second, different
 * state and measure something else.
 *
 * <h2>Why the depth counter and the latch are both required</h2>
 * {@code runExtraPass} calls {@code spawnForChunk}, which re-enters this injection. A depth counter alone is not
 * enough, because the counter is back at zero by the time the {@code RETURN} injection body runs - so every nested call
 * would look like the outermost one and start its own lane loop. That is unbounded recursion, and it produced an actual
 * {@code StackOverflowError} during testing. {@link SpawnLanes} therefore also holds an {@code IN_LANE} latch that
 * makes nested passes inert; the depth counter here only decides who gets to start the loop.
 *
 * <h2>Why {@code remap = false}</h2>
 * No refmap is shipped (plain {@code javac} build), so Mixin has no mapping table and must be told these names are
 * already production names.
 */
@Mixin(NaturalSpawner.class)
public abstract class SpawnLanesMixin {

    /** Nesting depth of {@code spawnForChunk} calls. Only a depth of 1 (the loop's own call) may add a lane. */
    @Unique
    private static final ThreadLocal<Integer> APOTH_LOCAL_DEPTH = ThreadLocal.withInitial(() -> 0);

    @Inject(method = "spawnForChunk", at = @At("HEAD"))
    private static void apotheosisExpansion$spawnLanesEnter(ServerLevel level, LevelChunk chunk,
                                                            NaturalSpawner.SpawnState state,
                                                            boolean spawnFriendlies, boolean spawnEnemies,
                                                            boolean forced, CallbackInfo ci) {
        APOTH_LOCAL_DEPTH.set(APOTH_LOCAL_DEPTH.get() + 1);
    }

    @Inject(method = "spawnForChunk", at = @At("RETURN"))
    private static void apotheosisExpansion$spawnLanesExit(ServerLevel level, LevelChunk chunk,
                                                           NaturalSpawner.SpawnState state,
                                                           boolean spawnFriendlies, boolean spawnEnemies,
                                                           boolean forced, CallbackInfo ci) {
        int depth = APOTH_LOCAL_DEPTH.get() - 1;
        APOTH_LOCAL_DEPTH.set(Math.max(0, depth));

        // Only the pass vanilla initiated may add a lane; nested passes are handled by the latch in SpawnLanes.
        if (depth == 0) {
            SpawnLanes.runExtraPass(level, chunk, state, 1, forced);
        }
    }
}
