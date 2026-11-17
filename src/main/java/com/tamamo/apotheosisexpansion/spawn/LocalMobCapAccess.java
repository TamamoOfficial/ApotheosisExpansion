package com.tamamo.apotheosisexpansion.spawn;

import net.minecraft.server.level.ChunkMap;

/**
 * Exposes {@code LocalMobCapCalculator.chunkMap} to {@link SpawnStateMixin}.
 *
 * <p><b>Why an accessor.</b> The level-wide cap check lives in {@code NaturalSpawner$SpawnState}, which holds a
 * {@code LocalMobCapCalculator} but has no way to reach the chunk it is being asked about. The only object that knows
 * both is the calculator, and it keeps the chunk map in a <em>private</em> field. {@code @Shadow} can only reach into
 * the class a mixin targets, so a second mixin implementing this interface is the minimal way to read it.
 *
 * <p>The contract is deliberately read-only: the spawn pipeline must never mutate the calculator.
 */
public interface LocalMobCapAccess {

    /** The chunk map this calculator belongs to. Never null in practice. */
    ChunkMap apotheosisExpansion$chunkMap();
}
