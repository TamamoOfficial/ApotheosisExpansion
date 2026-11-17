package com.tamamo.apotheosisexpansion.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.brigadier.context.CommandContext;
import com.tamamo.apotheosisexpansion.spawn.FakePlayerSummonTracker;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Captures who ran {@code /player <name> spawn}, so the fake it creates can inherit that player's world tier.
 *
 * <h2>Why this mixin exists at all</h2>
 * This is the only point in the whole call chain where the command source is still available. See
 * {@link FakePlayerSummonTracker} for the full explanation of where the information is lost: carpet calls
 * {@code createFake(...)}, which finishes asynchronously and never receives the source, so
 * {@code PlayerList.placeNewPlayer} (where the fake actually appears) has no way to know the caller.
 *
 * <h2>Why {@code spawn} and not {@code createFake}</h2>
 * {@code createFake}'s parameters are {@code (String, MinecraftServer, Vec3, double, double, ResourceKey, GameType,
 * boolean)} - no source. {@code PlayerCommand.spawn(CommandContext)} is the method that has it, and it calls
 * {@code createFake} synchronously, so recording at its HEAD is ordered before the fake can possibly exist.
 *
 * <h2>Why HEAD and not TAIL</h2>
 * The fake may be created before {@code spawn} returns (cached profile) or after (profile lookup). Recording at HEAD
 * covers both, because {@link FakePlayerSummonTracker} keeps the record alive for a bounded number of ticks.
 *
 * <h2>Command blocks</h2>
 * {@code CommandSourceStack.getPlayer()} is null for a command block, but {@code getEntity()} is not. Both are
 * forwarded; the tracker prefers the player and otherwise keeps the source entity's identity.
 *
 * <h2>Registration</h2>
 * This targets carpet's class, so it is gated by {@link com.tamamo.apotheosisexpansion.compat.CompatMixinPlugin} -
 * when carpet is not installed the mixin is never even resolved.
 *
 * <h2>Why {@code remap = false}</h2>
 * No refmap is shipped (this project compiles with plain {@code javac}), so every name here is a production name.
 */
@Mixin(targets = "carpet.commands.PlayerCommand")
public class CarpetPlayerCommandMixin {

    /**
     * Runs before carpet validates and creates anything.
     * <p>
     * The name argument is read through the command context rather than from a field, because that is where carpet
     * takes it from as well - so a renamed or aliased command cannot desynchronise the two.
     */
    @Inject(method = "spawn", at = @At("HEAD"))
    private static void apotheosisExpansion$recordSummoner(
        CommandContext<CommandSourceStack> ctx,
        CallbackInfoReturnable<Integer> cir
    ) {
        try {
            String fakeName = ctx.getArgument("player", String.class);
            CommandSourceStack source = ctx.getSource();

            ServerPlayer summoner = source.getPlayer();
            Entity sourceEntity = source.getEntity();
            FakePlayerSummonTracker.record(source.getServer(), fakeName, summoner, sourceEntity);
        }
        catch (IllegalArgumentException ex) {
            // The argument is not called "player", or the source was torn down mid-command. Recording a summon is a
            // convenience for tier inheritance; it must never be able to break the actual /player command.
        }
    }
}
