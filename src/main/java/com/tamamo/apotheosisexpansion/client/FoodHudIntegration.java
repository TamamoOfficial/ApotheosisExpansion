package com.tamamo.apotheosisexpansion.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.mojang.logging.LogUtils;

/**
 * Client-only HUD wiring for the {@code max_food} attribute.
 * <p>
 * Kept in its own package so the shared code paths never resolve {@code net.minecraft.client} types: this class is
 * only touched from the mod constructor behind an {@code FMLEnvironment.dist.isClient()} check.
 */
public final class FoodHudIntegration {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final String CLASSIC_BAR = "classicbar";

    private FoodHudIntegration() {}

    /**
     * Installs the multi-row hunger bar.
     * <p>
     * {@code wrapLayer} keeps the layer id, and that is what makes the integration with same-id HUD mods free of any
     * shared code: Classic Bar cancels {@code VanillaGuiLayers.FOOD_LEVEL} through
     * {@code RenderGuiLayerEvent.Pre} and matches it by name, so it cancels the wrapped layer exactly as it would
     * have cancelled the original, and {@link FoodHudLayer} simply never runs. Its own hunger bar is taught about the
     * attribute separately, inside that mod.
     * <p>
     * The wrap is installed unconditionally, even when Classic Bar is present. Skipping it there would be one line
     * shorter, but it would also mean "if Classic Bar is loaded and misbehaves, nobody draws a hunger bar at all" -
     * whereas leaving the layer in the chain degrades to this mod's rendering. The cancellation is the correct
     * arbiter, so it is left in charge.
     */
    public static void register(IEventBus modBus) {
        modBus.addListener(FoodHudIntegration::onRegisterGuiLayers);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.wrapLayer(VanillaGuiLayers.FOOD_LEVEL, FoodHudLayer::new);
        LOGGER.info("[ApotheosisExpansion] Hunger HUD layer wrapped for max_food (Classic Bar {})",
            isClassicBarLoaded() ? "detected - it will keep drawing its own bar" : "not installed");
    }

    /** Never throws: an unavailable {@code ModList} means "cannot tell", not "not installed". */
    private static boolean isClassicBarLoaded() {
        try {
            ModList modList = ModList.get();
            return modList != null && modList.isLoaded(CLASSIC_BAR);
        }
        catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }
}
