package com.tamamo.apotheosisexpansion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tamamo.apotheosisexpansion.FoodDataOwner;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * Binds each {@link FoodData} to the {@link Player} that constructed it.
 * <p>
 * <b>Why here and not in {@code FoodData}.</b> The field it needs to fill lives on {@code FoodData}, but the only
 * code that knows about both objects is the player's constructor. {@code FoodData#tick(Player)} also receives the
 * pair, and {@link FoodDataMixin} re-asserts the owner there, so this hook only has to cover the window between
 * construction and the first tick.
 * <p>
 * <b>Why {@code RETURN} of the constructor.</b> {@code FoodData} is created as a field initialiser, which javac
 * copies into every constructor <em>ahead of</em> the constructor body, so by {@code RETURN} the instance is already
 * in place. Injecting at {@code HEAD} would read the field before the initialiser ran and bind nothing.
 * <p>
 * <b>Why this cannot just be the {@code tick} hook.</b> {@code FoodData#tick} is only ever called from
 * {@code Player#tick} on the logical server. The client's copy - which drives the hunger bar rendering and is what
 * {@code ClientboundSetHealthPacket} feeds - would never get an owner, so its {@code add()} would keep clamping to
 * the vanilla 20.
 */
@Mixin(Player.class)
public abstract class PlayerFoodMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void apoth$bindFoodDataOwner(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        FoodData food = self.getFoodData();
        if (food instanceof FoodDataOwner owner) {
            owner.apoth$setOwner(self);
        }
    }
}
