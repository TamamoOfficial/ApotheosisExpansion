package com.tamamo.apotheosisexpansion.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.tamamo.apotheosisexpansion.compat.CompatMixinPlugin;
import com.tamamo.apotheosisexpansion.spawn.MobCapHudBridge;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

/**
 * Makes MiniHUD's mob-cap line show the real (scaled) cap instead of a hard-coded vanilla 70.
 *
 * <h2>The instruction being redirected</h2>
 * MiniHUD computes the cap itself - it never reads the result of the spawn check, so {@code MobCapScaler}'s two
 * spawn-pipeline redirects do not reach it. The formula is the vanilla one:
 * <pre>
 *   int cap = entry.getKey().getCapacity() * spawnableChunks / divisor;   // divisor = 289
 * </pre>
 * It appears once, inside the synthetic
 * {@code lambda$updateIntegratedServerMobCaps$1(MinecraftServer, ResourceKey)}, which runs on the integrated server's
 * thread. Redirecting the single {@code getMaxInstancesPerChunk()} call there substitutes one operand and leaves
 * MiniHUD's own arithmetic - the multiply by the spawnable chunk count and the divide by 289 - completely intact.
 *
 * <h2>Why the lambda and not the public method</h2>
 * {@code updateIntegratedServerMobCaps()} only schedules work; the computation lives in the lambda, which javac emits
 * as a private synthetic method. A redirect declared against the public method would match nothing and, with
 * {@code defaultRequire = 1}, fail the mixin at apply time. Verified against the built 0.32.60 jar: exactly one
 * {@code getMaxInstancesPerChunk} invocation, at offset 119 of that lambda.
 *
 * <h2>Why the level is resolved from the lambda parameters</h2>
 * Both values needed are already in scope: the {@code MinecraftServer} is the first lambda parameter and the
 * {@code ResourceKey<Level>} the second, so {@code server.getLevel(dimension)} yields exactly the dimension whose
 * numbers are being built. No {@code @Local} (which would need MixinExtras) is required.
 *
 * <h2>Cold switch</h2>
 * With {@code sync_mob_cap_to_hud} off, the bridge answers {@code null} and this handler returns the receiver's own
 * value - exactly what the unpatched call would have produced.
 *
 * <h2>Why {@code remap = false}</h2>
 * No refmap is shipped (this project compiles with plain {@code javac}), so Mixin has no mapping table and must be
 * told these are already production names.
 *
 * <h2>Why the target is gated by a plugin</h2>
 * The class belongs to MiniHUD. When MiniHUD is absent this mixin must never be attempted; see
 * {@link CompatMixinPlugin}, which checks the mod list before any resolution happens.
 */
@Mixin(targets = "fi.dy.masa.minihud.data.MobCapDataHandler")
public class MiniHudMobCapMixin {

    /**
     * Replaces {@code MobCategory.getMaxInstancesPerChunk()} inside the integrated-server cap calculation.
     *
     * @param receiver  the {@code MobCategory} the call was made on
     * @param server    the enclosing lambda's first parameter
     * @param dimension the enclosing lambda's second parameter
     */
    @Redirect(
        method = "lambda$updateIntegratedServerMobCaps$1(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/resources/ResourceKey;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"),
        remap = false)
    private int apotheosisExpansion$scaledCap(MobCategory receiver, MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server == null || dimension == null ? null : server.getLevel(dimension);
        Integer scaled = MobCapHudBridge.scaledCap(receiver, MobCapHudBridge.multiplierForLevel(level));
        if (com.tamamo.apotheosisexpansion.Config.debugMobCap()) {
            // Proof of life: this line appearing means the redirect is installed and MiniHUD really does run its own
            // cap calculation on this side. Its absence means MiniHUD never computes the cap itself (so it is reading
            // carpet/servux data instead) or the mixin did not apply.
            org.slf4j.LoggerFactory.getLogger("ApotheosisExpansion/HudHook").info(
                "[ApotheosisExpansion/HudHook] MiniHUD cap read: category={} vanilla={} scaled={} level={}",
                receiver, receiver.getMaxInstancesPerChunk(), scaled, dimension == null ? "-" : dimension.location());
        }
        return scaled != null ? scaled : receiver.getMaxInstancesPerChunk();
    }
}
