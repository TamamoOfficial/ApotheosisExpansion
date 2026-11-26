package com.tamamo.apotheosisexpansion.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import com.tamamo.apotheosisexpansion.ModAttributes;

/**
 * Renders the hunger bar for a player whose {@code max_food} exceeds the vanilla 20.
 * <p>
 * <b>Why a wrapper and not a replacement.</b> This layer is installed with
 * {@code RegisterGuiLayersEvent#wrapLayer}, so it holds the original vanilla layer and delegates to it whenever
 * {@code max_food} is at its default. That matters: the multi-row path below is a re-implementation, and a
 * re-implementation that silently differs from vanilla (a pixel off, the hurt-shake timing wrong) would be a
 * regression for every player who never sees this feature. At the default value nothing about the HUD changes,
 * because the code that runs is the original one.
 * <p>
 * <b>Why the vanilla code needs replacing at all.</b> {@code Gui#renderFood} is
 * {@code for (int j = 0; j < 10; j++)} - the icon count is a constant, not a function of the food level, so anything
 * above 20 half-drumsticks simply has nowhere to be drawn. The ceiling itself comes from the attribute
 * ({@link ModAttributes#maxFood}), which is synced to the client, so this layer knows the real maximum.
 * <p>
 * <b>Layout.</b> Icons fill like text: the bottom row is filled left-to-right (right-to-left in screen terms, since
 * the bar grows leftwards from {@code width/2 + 91}), and each further row is stacked 10 pixels higher, exactly
 * like the vanilla health bar stacks hearts. The {@code rightHeight} counter is advanced by 10 per row rather than
 * a flat 10, so the air bar and the selected-item name keep stacking above the taller bar instead of overlapping it.
 * <p>
 * <b>Classic Bar.</b> When Classic Bar is installed it cancels the {@code FOOD_LEVEL} layer outright and draws its
 * own bar, so this class never runs. That is the correct outcome rather than a conflict: Classic Bar's design is one
 * slot per stat, always full-width at that stat's maximum, and its {@code Hunger} overlay simply needed the same
 * denominator treatment. It reads the attribute itself ({@code compat/HungerCapacity} in that mod) and divides by it
 * exactly the way its {@code Health} overlay already divides by {@code getMaxHealth()}. No second bar, no overflow
 * panel - one slot, scaled.
 */
public final class FoodHudLayer implements LayeredDraw.Layer {

    /** Vanilla draws ten icons per row ({@code Gui.NUM_HEARTS_PER_ROW} equivalent for food). */
    private static final int ICONS_PER_ROW = 10;

    /** Horizontal step between icons and the panel offset, both straight from {@code Gui#renderFoodLevel}. */
    private static final int ICON_STEP = 8;
    private static final int ICON_SIZE = 9;
    private static final int ROW_HEIGHT = 10;

    /** Vanilla's own sprite ids; the client atlas resolves them without this mod shipping any texture. */
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final ResourceLocation FOOD_EMPTY_HUNGER = ResourceLocation.withDefaultNamespace("hud/food_empty_hunger");
    private static final ResourceLocation FOOD_HALF_HUNGER = ResourceLocation.withDefaultNamespace("hud/food_half_hunger");
    private static final ResourceLocation FOOD_FULL_HUNGER = ResourceLocation.withDefaultNamespace("hud/food_full_hunger");

    private final LayeredDraw.Layer vanilla;

    /**
     * Separate from {@code Gui.random}, which is private. Only used for the "no saturation" jitter, so a private
     * source with its own sequence is indistinguishable in practice.
     */
    private final RandomSource random = RandomSource.create();

    public FoodHudLayer(LayeredDraw.Layer vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker partialTick) {
        Player player = Minecraft.getInstance().getCameraEntity() instanceof Player p ? p : null;

        // Delegate whenever the ceiling is the vanilla one: the original layer is then exactly what should run.
        if (player == null || ModAttributes.maxFood(player) <= ModAttributes.VANILLA_MAX_FOOD) {
            this.vanilla.render(graphics, partialTick);
            return;
        }

        renderTallBar(graphics, player);
    }

    /**
     * The multi-row hunger bar. Mirrors {@code Gui#renderFoodLevel} for everything that is not the icon count.
     */
    private void renderTallBar(GuiGraphics graphics, Player player) {
        // Vanilla hides the hunger bar while riding something that shows its own health, because the vehicle
        // hearts take the same screen space.
        LivingEntity vehicle = vehicleWithHealth(player);
        if (vehicleMaxHearts(vehicle) != 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Gui gui = minecraft.gui;

        minecraft.getProfiler().push("food");

        int rightEdge = graphics.guiWidth() / 2 + 91;
        int bottomY = graphics.guiHeight() - gui.rightHeight;

        FoodData food = player.getFoodData();
        int foodLevel = food.getFoodLevel();
        int maxFood = ModAttributes.maxFood(player);

        // The bar is sized by the ceiling, not by the current value: the empty icons behind the full ones are what
        // tell the player how much room there is. One icon covers two half-drumsticks, so 30 needs 15 icons.
        int capacityIcons = Math.max(1, (maxFood + 1) / 2);
        int rows = Math.max(1, (capacityIcons + ICONS_PER_ROW - 1) / ICONS_PER_ROW);

        boolean hungry = player.hasEffect(MobEffects.HUNGER);
        ResourceLocation emptySprite = hungry ? FOOD_EMPTY_HUNGER : FOOD_EMPTY;
        ResourceLocation halfSprite = hungry ? FOOD_HALF_HUNGER : FOOD_HALF;
        ResourceLocation fullSprite = hungry ? FOOD_FULL_HUNGER : FOOD_FULL;

        boolean jitter = food.getSaturationLevel() <= 0.0F && gui.getGuiTicks() % (foodLevel * 3 + 1) == 0;
        int jitterOffset = jitter ? this.random.nextInt(3) - 1 : 0;

        for (int index = 0; index < capacityIcons; index++) {
            int column = index % ICONS_PER_ROW;
            int row = index / ICONS_PER_ROW;
            int x = rightEdge - column * ICON_STEP - ICON_SIZE;
            int y = bottomY - row * ROW_HEIGHT + jitterOffset;

            graphics.blitSprite(emptySprite, x, y, ICON_SIZE, ICON_SIZE);

            // Vanilla's comparison, kept verbatim: `j * 2 + 1 < i` is full, `== i` is half. Copying the exact
            // expressions rather than re-deriving "index < foodLevel / 2" is what makes the odd values (21, 23, ...)
            // come out identical to vanilla instead of one icon off.
            int covered = index * 2;
            if (covered + 1 < foodLevel) {
                graphics.blitSprite(fullSprite, x, y, ICON_SIZE, ICON_SIZE);
            }
            else if (covered + 1 == foodLevel) {
                graphics.blitSprite(halfSprite, x, y, ICON_SIZE, ICON_SIZE);
            }
        }

        // 10 per row: the air bar and the item name stack against this counter, so it has to grow with the bar.
        gui.rightHeight += rows * ROW_HEIGHT;

        minecraft.getProfiler().pop();
    }

    /** {@code Gui#getPlayerVehicleWithHealth}, which is private. */
    private static LivingEntity vehicleWithHealth(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof LivingEntity living ? living : null;
    }

    /** {@code Gui#getVehicleMaxHearts}, which is private. */
    private static int vehicleMaxHearts(LivingEntity vehicle) {
        if (vehicle == null || !vehicle.showVehicleHealth()) {
            return 0;
        }
        int hearts = (int) (vehicle.getMaxHealth() + 0.5F) / 2;
        return Math.min(hearts, 30);
    }
}
