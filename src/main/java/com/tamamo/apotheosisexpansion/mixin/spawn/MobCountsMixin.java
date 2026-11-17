package com.tamamo.apotheosisexpansion.mixin.spawn;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tamamo.apotheosisexpansion.spawn.MobCapScaler;

import net.minecraft.world.entity.MobCategory;

/**
 * Scales the per-player monster cap inside {@code LocalMobCapCalculator$MobCounts.canSpawn}.
 *
 * <p><b>The vanilla line being changed.</b>
 * {@code counts.getOrDefault(category, 0) < category.getMaxInstancesPerChunk()} - the right-hand side is the raw 70
 * for {@code MONSTER} that this feature exists to widen.
 *
 * <p><b>Why a redirect on the getter.</b> The comparison's limit is not a literal in the bytecode; it is a virtual
 * call to {@code MobCategory.getMaxInstancesPerChunk()}. Redirecting that call replaces exactly the number vanilla
 * compares against while leaving the counting and the loop structure alone, so the "any player with room wins" rule
 * is preserved - only the size of "room" changes.
 *
 * <p><b>Why the handler takes two parameters.</b> A {@code @Redirect} on an <em>instance</em> method call receives the
 * receiver first, then the call's own arguments, then the enclosing method's parameters. This call has no arguments
 * of its own, so the handler is {@code (receiver, enclosingParam)} - a one-parameter handler would be rejected at
 * apply time because the arity does not match.
 *
 * <p><b>Why the multiplier is read from the stack.</b> This class is only ever entered from
 * {@code LocalMobCapCalculator.canSpawn}, which pushes the multiplier for its chunk first. When nothing was pushed -
 * config off, or the method called from somewhere unexpected - {@link MobCapScaler#currentMultiplier()} returns 1 and
 * this redirect returns vanilla's own value unchanged.
 *
 * <p><b>Why {@code remap = false}.</b> This project compiles with plain {@code javac} and ships no refmap, so Mixin has
 * no mapping table to translate names through. Leaving remapping on would make it look for the runtime-obfuscated name
 * of {@code getMaxInstancesPerChunk} and fail to find the call. Every name here is already a production name.
 *
 * <p><b>Package-private target.</b> {@code MobCounts} is package-private, so it is named by its binary form
 * ({@code $} for the nesting); importing the type would not compile.
 */
@Mixin(targets = "net.minecraft.world.level.LocalMobCapCalculator$MobCounts")
public class MobCountsMixin {

    @Redirect(
        method = "canSpawn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"),
        remap = false)
    private int apotheosisExpansion$scaledPerPlayerCap(MobCategory receiver, MobCategory category) {
        return MobCapScaler.scaleCap(receiver, receiver.getMaxInstancesPerChunk(), MobCapScaler.currentMultiplier());
    }
}
