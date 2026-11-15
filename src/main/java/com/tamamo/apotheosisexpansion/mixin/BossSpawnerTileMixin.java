package com.tamamo.apotheosisexpansion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tamamo.apotheosisexpansion.compat.InvaderSpawnerFilter;

import dev.shadowsoffire.apotheosis.mobs.registries.InvaderRegistry;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.tiers.GenContext;

/**
 * Keeps this mod's added invaders out of Apotheosis' {@code boss_spawner} block.
 * <p>
 * <b>Why the lambda, not {@code serverTick}.</b> {@code BossSpawnerTile.serverTick} hands the work to
 * {@code Optional.ifPresent(player -> ...)}, so the whole spawn body - including the
 * {@code InvaderRegistry.getRandomItem(ctx)} call - lives in the synthetic method
 * {@code lambda$serverTick$0(BlockPos, Player)V}. A redirect declared against {@code serverTick} would match nothing
 * and, with the project's {@code defaultRequire=1}, fail the mixin at apply time. Verified against the 8.9.0 jar:
 * {@code javap -c} shows exactly one {@code getRandomItem} invocation and it is inside that lambda.
 * <p>
 * <b>Why a redirect on the call site rather than on the registry.</b> {@code InvaderRegistry} has five callers of
 * {@code getRandomItem(GenContext)} - this spawner block, the {@code /apoth bosses} command, the boss summoner item,
 * the gateways wave entity, and the natural-spawn path in {@code ApothMobEvents}. Only the spawner needs filtering.
 * Redirecting this single invocation leaves the other four untouched, and avoids a call-stack test that could not
 * tell a spawner apart from a command run in the same tick.
 * <p>
 * <b>What filtering means.</b> Dungeon spawners are always unbound: Apotheosis places them with
 * {@code Level.setBlock(pos, ..., 2)}, which does not create a block entity, so {@code item} stays
 * {@code emptyHolder()} and the block falls back to a random pick. That pick is already constrained by
 * {@code Constraints.eval(ctx)} (dimension / biome / world tier), so this filter does not change <i>where</i> an
 * invader may appear - it only stops this mod's entries from joining the pool, so an Apotheosis dungeon keeps
 * spawning the bosses it was designed around.
 */
@Mixin(targets = "dev.shadowsoffire.apotheosis.mobs.BossSpawnerBlock$BossSpawnerTile")
public class BossSpawnerTileMixin {

    /**
     * Replaces {@code InvaderRegistry.INSTANCE.getRandomItem(ctx)} inside the spawn lambda.
     * <p>
     * {@code remap = false} is required: the target is a modded method, not a Minecraft/NeoForge one, so it must not
     * go through the obfuscation remapper.
     */
    @Redirect(
        method = "lambda$serverTick$0(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V",
        at = @At(
            value = "INVOKE",
            target = "Ldev/shadowsoffire/apotheosis/mobs/registries/InvaderRegistry;getRandomItem(Ldev/shadowsoffire/apotheosis/tiers/GenContext;)Ldev/shadowsoffire/apotheosis/mobs/types/Invader;"),
        remap = false)
    private Invader apotheosisExpansion$filterSpawnerPick(InvaderRegistry registry, GenContext ctx) {
        Invader picked = registry.getRandomItem(ctx);
        return InvaderSpawnerFilter.replaceIfAdded(picked, ctx);
    }
}
