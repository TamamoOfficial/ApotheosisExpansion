package com.tamamo.apotheosisexpansion.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tamamo.apotheosisexpansion.spawn.MobCapHudBridge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;

/**
 * Makes Servux broadcast the real (scaled) cap to clients instead of the vanilla 70.
 *
 * <h2>Why this is needed even though Servux is optional</h2>
 * MiniHUD prefers data pushed by Servux over its own local calculation - {@code subscribedServerData} is checked
 * before {@code localData}. So on a dedicated server the number a player sees comes from here, not from
 * {@link MiniHudMobCapMixin}. Both sites have to be patched, or the display silently reverts to 70 the moment Servux
 * is installed.
 *
 * <h2>The instruction being redirected</h2>
 * {@code DataLoggerMobCaps#getResult} builds one {@code CompoundTag} per dimension:
 * <pre>
 *   int capacity = entry.getKey().getCapacity() * spawnableChunks / divisor;   // divisor = 289
 * </pre>
 * Verified against the built 0.3.17 jar: exactly one {@code getMaxInstancesPerChunk} invocation, at offset 189 of
 * {@code getResult}. The redirect substitutes only that operand; Servux's own chunk counting and division stay intact.
 *
 * <h2>Why the level is not resolved here</h2>
 * {@code getResult} loops over {@code server.getWorlds()}, and the loop variable is not a parameter, so without
 * MixinExtras there is no clean way to read it. The bridge therefore resolves the maximum across dimensions, which is
 * safe because the value only ever changes a displayed number and the HUD reports one figure anyway. If the level
 * cannot be resolved at all the bridge answers {@code null} and the vanilla value is returned unchanged, so the patch
 * degrades to a no-op rather than to a wrong number.
 *
 * <h2>Cold switch</h2>
 * With {@code sync_mob_cap_to_hud} off this patch is a no-op.
 *
 * <h2>Why {@code remap = false}</h2>
 * No refmap is shipped, so every name here is already a production name.
 */
@Mixin(targets = "fi.dy.masa.servux.loggers.DataLoggerMobCaps")
public class ServuxMobCapMixin {

    @Redirect(
        method = "getResult",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"),
        remap = false)
    private int apotheosisExpansion$scaledCap(MobCategory receiver, MinecraftServer server) {
        Integer scaled = MobCapHudBridge.scaledCap(receiver, MobCapHudBridge.multiplierForServer(server));
        return scaled != null ? scaled : receiver.getMaxInstancesPerChunk();
    }
}
