package com.tamamo.apotheosisexpansion;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The custom attribute this mod adds to players.
 * <p>
 * <b>Why a mod-side patch is unavoidable.</b> 1.21.1 has no hunger attribute at all. The whole model is two plain
 * fields on {@link net.minecraft.world.food.FoodData} with the ceiling {@code 20} written as a literal at every
 * site that touches it, so an attribute on its own would give Apotheosis affixes a name to put in a tooltip and
 * nothing else - the very first bite of food would clamp the bar straight back to 20. The attribute is therefore
 * only half the feature; {@code FoodDataMixin} and {@code PlayerFoodMixin} are the other half.
 * <p>
 * <b>Why every read goes through {@link #maxFood(Player)}.</b> {@code AttributeMap#getInstance} returns {@code null}
 * when the entity's supplier has no entry for the attribute, and {@code AttributeSupplier} does not fall back to
 * the default value - it throws. {@code LivingEntity#collectEquipmentChanges} silently drops a modifier in that
 * case, so a missing registration shows up as "the affix does nothing" rather than as an error. The helper never
 * throws and falls back to the vanilla ceiling.
 * <p>
 * <b>Why only {@code max_food} and not a saturation ceiling.</b> The vanilla bound on saturation is
 * {@code Mth.clamp(..., 0.0F, (float) foodLevel)} in {@code FoodData#add} - the food level itself, not 20 - so
 * raising {@code max_food} already raises the saturation bound to the new ceiling. A separate saturation attribute
 * above 20 would therefore do nothing at all, and one below 20 would only ever be a nerf.
 */
public final class ModAttributes {

    /** The vanilla ceiling, used both as this attribute's default and as the fallback for unresolved reads. */
    public static final int VANILLA_MAX_FOOD = 20;

    /** Mirrors the attribute's own upper bound; also keeps a runaway modifier from shaping the HUD later. */
    private static final int HARD_MAX_FOOD = 1024;

    private static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, ApotheosisExpansion.MODID);

    /**
     * Upper bound of the hunger bar, in half-drumsticks ({@code FoodData#getFoodLevel}'s unit).
     * <p>
     * Syncable so the client's {@code FoodData} enforces the same ceiling as the server: {@code FoodData#add} runs
     * on both sides, and a client still clamping to 20 would fight the server's value on every bite.
     * <p>
     * {@code RangedAttribute} rather than the plain base class so the value is clamped to a sane range before this
     * mod ever sees it, and so ApothicAttributes' attribute screen renders a numeric field instead of a toggle.
     */
    public static final DeferredHolder<Attribute, Attribute> MAX_FOOD = ATTRIBUTES.register(
        "max_food",
        () -> new RangedAttribute("attribute." + ApotheosisExpansion.MODID + ".max_food",
            VANILLA_MAX_FOOD, 1.0D, HARD_MAX_FOOD)
            .setSyncable(true));

    private ModAttributes() {}

    /**
     * Hooks the registry and the entity-type wiring onto the mod bus.
     * <p>
     * The {@link EntityAttributeModificationEvent} half is load-bearing rather than cosmetic: Apotheosis turns an
     * affix into an {@code AttributeModifier} and {@code LivingEntity#collectEquipmentChanges} discards it without
     * a log line when there is no instance to attach it to. Without this listener the affix would render in the
     * tooltip and have no effect whatsoever.
     * <p>
     * Both buses matter: the event fires on the mod bus, but it fires on the client too, which is what lets the
     * client's own {@code AttributeSupplier} resolve {@code max_food} when it re-applies equipment modifiers
     * locally.
     */
    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(ModAttributes::addToPlayers);
    }

    private static void addToPlayers(EntityAttributeModificationEvent event) {
        // Player only. FoodData exists on Player and on nothing else, so handing this to every LivingEntity would
        // burn an attribute slot and sync bandwidth on every mob for a value nothing reads.
        event.add(EntityType.PLAYER, MAX_FOOD);
    }

    /**
     * Resolves the player's hunger ceiling, falling back to {@link #VANILLA_MAX_FOOD} whenever it cannot be read.
     * <p>
     * Safe with a {@code null} player (a {@code FoodData} that has not been bound to its owner yet) and safe while
     * the attribute map is mid-update.
     *
     * @param player the player whose ceiling is wanted, may be {@code null}
     * @return the ceiling in half-drumsticks, always at least 1
     */
    public static int maxFood(Player player) {
        if (player == null) {
            return VANILLA_MAX_FOOD;
        }
        AttributeInstance instance = player.getAttribute(MAX_FOOD);
        if (instance == null) {
            // The player type was never given the attribute - a registration problem, not a gameplay one.
            return VANILLA_MAX_FOOD;
        }
        double value = instance.getValue();
        if (!(value >= 1.0D)) {
            // NaN and non-positive both land here. A NaN upper bound would turn every clamp into a no-op.
            return VANILLA_MAX_FOOD;
        }
        return (int) Math.min(HARD_MAX_FOOD, Math.floor(value));
    }
}
