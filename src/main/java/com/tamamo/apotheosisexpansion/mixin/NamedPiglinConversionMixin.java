package com.tamamo.apotheosisexpansion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tamamo.apotheosisexpansion.Config;
import com.tamamo.apotheosisexpansion.NamedMobPeacefulProtection;

import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/**
 * Stops a player-named piglin / piglin brute from zombifying when it lingers in the Overworld (or End).
 *
 * <p><b>The vanilla rule being extended.</b> {@code AbstractPiglin#isConverting} gates the whole conversion:
 * <pre>
 *   return !this.level().dimensionType().piglinSafe() &amp;&amp; !this.isImmuneToZombification() &amp;&amp; !this.isNoAi();
 * </pre>
 * and {@code customServerAiStep} counts {@code timeInOverworld} up on that gate alone, converting at 300 ticks
 * (15 seconds). Only two things ever set {@code isImmuneToZombification}: the {@code IsImmuneToZombification} NBT
 * flag (read back in {@code readAdditionalSaveData}) and {@code PiglinConverter} for piglins that arrive through a
 * nether portal. A name tag is not one of them, so a pet piglin walked over to the Overworld still becomes a
 * zombified piglin after fifteen seconds and the player loses the mob they named.
 *
 * <p><b>Why the tag instead of {@code hasCustomName}.</b> Exactly the reasoning of
 * {@link NamedMobPeacefulProtection}: Apotheosis stamps generated names on invaders, spawn eggs can carry a name
 * component and {@code convertTo} copies names, so a plain name check would switch off zombification for mobs the
 * player never touched. The {@code apotheosis_expansion.named} scoreboard tag is only ever applied by a player
 * using a name tag, and it is also what {@code /data get entity} and {@code /tag} expose for scripted setups.
 *
 * <p><b>Why mixin and not the NeoForge event.</b> {@code LivingConversionEvent.Pre} would also work (cancelling it
 * skips the conversion), but it fires once per tick from the moment the timer passes 300 and does not stop
 * {@code timeInOverworld} from counting, and it cannot be gated on the tag as cheaply for the 90 % of ticks where
 * nothing is converting. Here the tag short-circuits inside {@code isConverting}, which is the single gate
 * {@code AbstractPiglin#customServerAiStep} uses both to count {@code timeInOverworld} and to convert - so the
 * timer never even starts, and the mob keeps its normal behaviour instead of retreating from its own conversion
 * ({@code PiglinAi} picks the retreat sound through the same gate).
 *
 * <p><b>Only the zombification gate is touched.</b> Injecting at the HEAD of {@code isConverting} and forcing
 * {@code cir.setReturnValue(false)} when the tag is present cannot affect anything else: {@code isConverting} is
 * only consulted to decide "should this piglin be turning", and Hoglin declares its own separate
 * {@code isConverting} instead of inheriting this one, so the mixin structurally cannot reach it - the feature is
 * about piglins, as asked.
 *
 * <p><b>{@code remap = false}.</b> Same reason as every other mixin here (CreativeTargetFilter spells it out):
 * this project compiles with plain {@code javac} and ships no refmap, so a remapped name would be looked up in
 * Mixin's non-existent mapping table and fail at apply time. {@code isConverting} is written in its production
 * name, which is also the name the dev Minecraft jar carries.
 *
 * <p><b>Callback type.</b> {@code isConverting} returns {@code boolean}, so the handler takes
 * {@link CallbackInfoReturnable} with {@code cancellable = true} and only ever sets a return value.
 */
@Mixin(AbstractPiglin.class)
public abstract class NamedPiglinConversionMixin {

    @Inject(method = "isConverting", at = @At("HEAD"), cancellable = true, remap = false)
    private void apotheosisExpansion$namedPiglinNeverZombifies(CallbackInfoReturnable<Boolean> cir) {
        if (!Config.enableNamedPiglinNoZombify()) {
            return;
        }
        AbstractPiglin self = (AbstractPiglin) (Object) this;
        if (self.getTags().contains(NamedMobPeacefulProtection.PROTECTED_TAG)) {
            cir.setReturnValue(false);
        }
    }
}
