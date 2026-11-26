package com.tamamo.apotheosisexpansion;

import net.minecraft.world.entity.player.Player;

/**
 * Exposes the owner of a {@link net.minecraft.world.food.FoodData}.
 * <p>
 * <b>Why the owner has to be pushed in from outside.</b> {@code FoodData} is created with {@code new FoodData()} and
 * has no reference to its player - it declares five fields and not one of them is Player-typed - yet every ceiling it
 * enforces depends on that player's attributes. The two places that know both objects are {@code Player.<init>},
 * which creates the instance, and {@code FoodData.tick(Player)}, which is handed the player every tick. Both are
 * wired up in {@code FoodDataMixin}, and this interface is how the constructor-side hook reaches the field.
 * <p>
 * <b>Why it is not in the {@code mixin} package.</b> Everything under {@code ...apotheosisexpansion.mixin} is
 * expected to be a {@code @Mixin} class - {@code build/verify_hud_compat.js} enforces that, and a stray
 * implementation in there would be picked up by the mixin config's package scan. This is ordinary code that the
 * mixin implements, so it lives next to {@link ModAttributes}.
 */
public interface FoodDataOwner {

    /** Binds this food data to the player whose attributes it should respect. */
    void apoth$setOwner(Player player);
}
