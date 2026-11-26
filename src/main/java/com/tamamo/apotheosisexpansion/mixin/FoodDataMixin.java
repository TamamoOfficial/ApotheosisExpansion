package com.tamamo.apotheosisexpansion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tamamo.apotheosisexpansion.FoodDataOwner;
import com.tamamo.apotheosisexpansion.ModAttributes;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * Raises the hard-coded {@code 20} ceilings in {@link FoodData} to the player's {@code max_food} attribute.
 * <p>
 * <b>Every site has to be patched, because the ceiling is a literal in four places.</b> Patching only {@code add()},
 * the obvious one, produces a bar that fills to the new ceiling and then collapses back to 20 on the very next bite:
 * {@code eat()} funnels through {@code add()}, whose {@code Mth.clamp(..., 0, 20)} rewrites 30 to 20. The others are
 * behaviour rather than storage:
 * <ul>
 * <li>{@code add()} - the food and saturation clamps themselves.</li>
 * <li>{@code needsFood()} - decides whether a player may eat at all and whether the Peaceful refill runs.</li>
 * <li>{@code tick()} - the saturated-regeneration gate ({@code foodLevel >= 20}) and the ordinary one
 * ({@code foodLevel >= 18}). Left alone, a player with a raised ceiling would keep regenerating on the fast,
 * saturation-free branch from 20 upward.</li>
 * </ul>
 * <p>
 * <b>Why the literal and not {@code FoodConstants.MAX_FOOD}.</b> That constant exists in 1.21.1 but is dead: nothing
 * in vanilla reads it (only {@code saturationByModifier} is used) and the clamps carry the literal, so changing the
 * constant would do nothing. Each patch below therefore targets a constant in one specific method, and each of those
 * methods contains its literal exactly once - no {@code ordinal} is needed and none would be safe to add.
 * <p>
 * <b>Saturation needs no patch.</b> Its clamp is {@code Mth.clamp(..., 0.0F, (float) foodLevel)} - the bound is the
 * food level itself, not 20 - so once the food clamp is raised, saturation follows automatically. That is also why
 * this mod does not add a "max saturation" attribute: there is nothing left to override.
 * <p>
 * <b>Owner binding.</b> {@code FoodData} does not know its player. {@link #apoth$owner} is filled in by
 * {@link PlayerFoodMixin} right after construction and re-asserted by {@code tick(Player)}, and every read goes
 * through {@link #apoth$maxFood()}, which falls back to 20 while the owner is still {@code null}.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin implements FoodDataOwner {

    /** The player this food data belongs to; {@code null} until the constructor or tick hook binds it. */
    @Unique
    private Player apoth$owner;

    @Override
    public void apoth$setOwner(Player player) {
        this.apoth$owner = player;
    }

    /**
     * The ceiling this instance clamps against, in half-drumsticks.
     * <p>
     * Falls back to the vanilla value instead of a sentinel: every caller is a clamp, and 20 is exactly what the
     * vanilla literal would have provided.
     */
    @Unique
    private int apoth$maxFood() {
        return this.apoth$owner == null ? ModAttributes.VANILLA_MAX_FOOD : ModAttributes.maxFood(this.apoth$owner);
    }

    /**
     * Re-asserts the owner and pulls the current values back under the ceiling.
     * <p>
     * The owner re-assertion is belt and braces next to the constructor hook, and it is what makes a {@code FoodData}
     * swapped in later work without an extra hook ({@code ServerPlayer#restoreFrom} reassigns the field wholesale,
     * so the instance carrying the constructor-bound owner is thrown away).
     * <p>
     * The clamp is not redundant with the ones in {@code add()}, because those only run when food is eaten. Taking a
     * {@code +max_food} affix off leaves the player at 30 of a now-20 bar: {@code needsFood()} says they are not
     * hungry, so {@code canEat} refuses every item and there is no way to eat back down. The same applies to a save
     * written while an affix was equipped.
     * <p>
     * Runs on the logical server only - {@code Player#tick} gates {@code FoodData#tick} behind
     * {@code !level().isClientSide}. The client applies the same ceiling in its own {@code add()} calls, and the
     * authoritative value arrives through {@code ClientboundSetHealthPacket} anyway.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void apoth$bindOwnerAndClamp(Player player, CallbackInfo ci) {
        this.apoth$owner = player;

        FoodData self = (FoodData) (Object) this;
        int max = this.apoth$maxFood();
        if (self.getFoodLevel() > max) {
            self.setFoodLevel(max);
        }
        float saturationCap = Math.min(self.getFoodLevel(), max);
        if (self.getSaturationLevel() > saturationCap) {
            self.setSaturation(saturationCap);
        }
    }

    /**
     * {@code add()}: {@code Mth.clamp(sum, 0, 20)}.
     * <p>
     * Only the upper bound changes; the sum and the lower bound stay exactly as vanilla computes them.
     */
    @ModifyConstant(method = "add", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 20))
    private int apoth$clampFood(int original) {
        return this.apoth$maxFood();
    }

    /**
     * {@code needsFood()}: {@code return this.foodLevel < 20;}.
     * <p>
     * Drives both "may I eat" ({@code Player#canEat}) and the Peaceful refill. With the literal, a player at 25 of 30
     * is told they are not hungry and cannot eat at all.
     */
    @ModifyConstant(method = "needsFood", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 20))
    private int apoth$needsFood(int original) {
        return this.apoth$maxFood();
    }

    /**
     * {@code tick()}: the saturated-regeneration gate {@code this.foodLevel >= 20}.
     */
    @ModifyConstant(method = "tick", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 20))
    private int apoth$saturatedRegenGate(int original) {
        return this.apoth$maxFood();
    }

    /**
     * {@code tick()}: the ordinary-regeneration gate {@code this.foodLevel >= 18}.
     * <p>
     * Vanilla means "two below full", not the absolute 18, so that relationship is preserved.
     */
    @ModifyConstant(method = "tick", constant = @org.spongepowered.asm.mixin.injection.Constant(intValue = 18))
    private int apoth$healLevel(int original) {
        return Math.max(0, this.apoth$maxFood() - 2);
    }
}
