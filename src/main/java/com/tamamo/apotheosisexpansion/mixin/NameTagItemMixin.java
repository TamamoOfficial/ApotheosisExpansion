package com.tamamo.apotheosisexpansion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tamamo.apotheosisexpansion.NamedMobPeacefulProtection;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;

/**
 * Tags a mob with {@link NamedMobPeacefulProtection#PROTECTED_TAG} when a player applies a name tag to it.
 *
 * <p><b>Why a mixin at all.</b> NeoForge has no dedicated name-tag event: {@code NameTagItem#interactLivingEntity}
 * is a plain vanilla method, and the only surrounding hooks are {@code PlayerInteractEvent.EntityInteract} (fired
 * before the item is used, so it cannot tell whether the tag actually applied) and
 * {@code EntityJoinLevelEvent} (fires when the mob enters the world, long before it is named). Injecting into the
 * item method is the only place where the name is already set and the operation has succeeded.
 *
 * <p><b>Why {@code INVOKE ItemStack.shrink} and not {@code TAIL}.</b> The method has <em>two</em> returns:
 * <pre>
 *    62: invokevirtual Mob.setPersistenceRequired()V
 *    67: invokevirtual ItemStack.shrink(I)V          &lt;- success path \
 *    71: invokevirtual Level.isClientSide()Z                          | falls through to
 *    77: invokestatic InteractionResult.sidedSuccess(Z)InteractionResult
 *    80: areturn                                                     &lt;- first return  /
 *    81: getstatic InteractionResult.PASS
 *    84: areturn                                                     &lt;- second return
 * </pre>
 * {@code @At("TAIL")} means "the <i>last</i> RETURN", which is the {@code PASS} branch - the path taken when the
 * stack has no name or the target is a player, i.e. when nothing happened. A {@code TAIL} injection therefore
 * never fires on a successful rename. This was confirmed in-game: the first build renamed a zombie correctly but
 * the tag was never applied.
 * <p>
 * {@code RETURN; ordinal = 0} would work, but it depends on how many returns vanilla has and in what order. The
 * {@code ItemStack.shrink} call is a named anchor that exists only on the success path (the item consumes one from
 * the stack exactly once, after the name was set), so the injection point cannot silently drift with an unrelated
 * edit to the method.
 *
 * <p><b>Why the guards are still needed.</b> The success branch is server-only for the entity mutation, but the
 * method itself also runs on the client, where the same bytecode path runs and only {@code sidedSuccess} differs.
 * The explicit {@code isClientSide} test keeps the tag off the client copy; the tag would be useless there anyway,
 * since despawn logic only runs on the server.
 *
 * <p><b>Callback type.</b> An {@code @Inject} callback type is decided by the <i>target method's</i> return type,
 * not by where in the method the injection point sits. {@code interactLivingEntity} returns
 * {@link InteractionResult}, so the handler must declare {@link CallbackInfoReturnable} even though this anchor is
 * mid-method and the value is never read; plain {@code CallbackInfo} is rejected at apply time with
 * "CallbackInfoReturnable is required", which is exactly what crashed the client twice.
 *
 * <p><b>No {@code setReturnValue}.</b> The injection must not change what the item returns, so the
 * {@code cir} parameter is accepted but never used.
 */
@Mixin(NameTagItem.class)
public abstract class NameTagItemMixin {

    /**
     * Runs immediately before the item consumes the name tag, i.e. only after the rename succeeded.
     * <p>
     * No custom-name check is needed here: the only way to reach this call is the branch guarded by
     * {@code stack.get(DataComponents.CUSTOM_NAME) != null}.
     */
    @Inject(
        method = "interactLivingEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void apotheosisExpansion$tagPlayerNamedMob(
        ItemStack stack,
        Player player,
        LivingEntity target,
        InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (player.level().isClientSide || !target.isAlive()) {
            return;
        }
        if (target instanceof Mob mob) {
            mob.addTag(NamedMobPeacefulProtection.PROTECTED_TAG);
        }
    }
}
