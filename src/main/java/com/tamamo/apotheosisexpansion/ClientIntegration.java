package com.tamamo.apotheosisexpansion;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only wiring, kept in its own class so the dedicated-server class loader never has to resolve
 * {@code net.minecraft.client} or {@code net.neoforged.neoforge.client} types.
 * <p>
 * Referenced only behind an {@code FMLEnvironment.dist.isClient()} check in the mod constructor.
 */
final class ClientIntegration {

    private ClientIntegration() {}

    /**
     * Registers NeoForge's generic config screen for this mod.
     * <p>
     * {@code ModListScreen} enables the "Config" button only when
     * {@code IConfigScreenFactory.getForMod(...)} is present, and NeoForge registers that extension point exclusively
     * for its own container. Registering {@code ConfigurationScreen::new} here reuses the built-in screen, so the
     * toggles appear in the mod list with no custom GUI code.
     */
    static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
