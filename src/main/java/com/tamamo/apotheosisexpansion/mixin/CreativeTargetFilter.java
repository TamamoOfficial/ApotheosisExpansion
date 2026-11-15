package com.tamamo.apotheosisexpansion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Stops Apotheosis' invader bosses from force-targeting creative and spectator players.
 *
 * <p><b>The bug.</b> Vanilla AI already refuses creative/spectator targets:
 * {@code NearestAttackableTargetGoal} runs {@code TargetingConditions.forCombat()}, which drops candidates failing
 * {@code Player.canBeSeenAsEnemy()}, and that returns {@code !getAbilities().invulnerable && ...} - false for both
 * creative and spectator. So a boss spawned with no forced target never locks onto them.
 * Apotheosis bypasses that by calling {@code Mob.setTarget(player)} directly, and the two call sites are both
 * incomplete:
 * <ul>
 * <li>{@code ApothMobEvents.trySpawnInvader} guards with {@code !player.isCreative()} - which excludes creative but
 * <b>not spectator</b>, even though a spectator is equally {@code invulnerable}.</li>
 * <li>{@code BossSpawnerBlock$BossSpawnerTile} picks its trigger player with
 * {@code EntitySelector.NO_CREATIVE_OR_SPECTATOR}, so the spawn only fires for a survival player - but the block does
 * not re-check between that eight-block scan and the {@code setTarget} call forty ticks into the loop, and the target
 * is assigned unconditionally. A player who switches mode inside that window still gets locked on.</li>
 * </ul>
 *
 * <p><b>The fix.</b> Redirect each {@code Mob.setTarget} call and skip it when the target fails the very predicate
 * vanilla uses, {@link EntitySelector#NO_CREATIVE_OR_SPECTATOR} ({@code !isSpectator() && !isCreative()}). This is a
 * no-op for every legitimate target, so survival difficulty and behaviour are unchanged.
 * Nothing else needs patching: with no forced target, the vanilla goal selector will not select a creative or
 * spectator player by itself.
 *
 * <p><b>Why {@code remap = false}.</b> This project compiles with plain {@code javac} and ships no refmap (there is no
 * Mixin annotation processor and {@code apotheosis_expansion.mixins.json} declares no {@code refmap}), so there is no
 * mapping table for Mixin to translate names through. Leaving remapping enabled would make the injector look for the
 * runtime-obfuscated name of {@code setTarget} and fail to find the call. The enclosing classes and methods are
 * modded Apotheosis names that are never obfuscated anyway, so {@code false} is correct for the whole annotation.
 */
public final class CreativeTargetFilter {

    private CreativeTargetFilter() {}

    /**
     * Assigns the target unless it is a creative or spectator player.
     * <p>
     * Shared by both mixins so the rule cannot drift between the natural-spawn path and the spawner path.
     */
    public static void setTargetIfValid(Mob mob, LivingEntity target) {
        if (target instanceof Player player && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) {
            // Creative or spectator: leave the boss without a forced target so vanilla AI keeps ignoring them.
            return;
        }
        mob.setTarget(target);
    }

    /** Natural-spawn path in {@code ApothMobEvents.trySpawnInvader}. */
    @Mixin(targets = "dev.shadowsoffire.apotheosis.mobs.ApothMobEvents")
    public static class NaturalSpawn {

        @Redirect(
            method = "trySpawnInvader",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/entity/Mob;setTarget(Lnet/minecraft/world/entity/LivingEntity;)V"),
            remap = false)
        private void apotheosisExpansion$guardTarget(Mob mob, LivingEntity target) {
            setTargetIfValid(mob, target);
        }
    }

    /** Boss spawner block path, inside the {@code Optional.ifPresent} lambda. */
    @Mixin(targets = "dev.shadowsoffire.apotheosis.mobs.BossSpawnerBlock$BossSpawnerTile")
    public static class BossSpawner {

        @Redirect(
            method = "lambda$serverTick$0(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/entity/Mob;setTarget(Lnet/minecraft/world/entity/LivingEntity;)V"),
            remap = false)
        private void apotheosisExpansion$guardTarget(Mob mob, LivingEntity target) {
            setTargetIfValid(mob, target);
        }
    }
}
