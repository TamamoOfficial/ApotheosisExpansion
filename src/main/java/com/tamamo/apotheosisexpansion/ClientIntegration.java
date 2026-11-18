package com.tamamo.apotheosisexpansion;

import javax.annotation.Nullable;

import com.tamamo.apotheosisexpansion.spawnerrune.SpawnerRuneTooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

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

    /**
     * Registers the spawner-rune tooltip listeners.
     * <p>
     * {@link RecipesUpdatedEvent} is a client-only event, which is why this wiring lives here rather than in the shared
     * integration class: registering it from common code would make a dedicated server resolve a client event class.
     * <p>
     * Only called after {@code SpawnerRuneIntegration.isAvailable()}, so the listener bodies - which reference Apothic
     * Spawners types - are never reached on a pack that lacks that mod.
     */
    static void registerSpawnerRuneTooltips() {
        // A plain listener object rather than lambdas passed to addListener.
        //
        // The generic form `addListener(EventPriority, Class, Consumer<T>)` infers T from the lambda's parameter, and
        // that inference does not reliably pin the event class when the consumer is a lambda - the listener ends up
        // registered but never matches. A @SubscribeEvent method has the event type in its signature, which the bus
        // reads reflectively, so there is nothing to infer.
        NeoForge.EVENT_BUS.register(new Object() {

            @SubscribeEvent(priority = EventPriority.LOW)
            public void onTooltip(ItemTooltipEvent event) {
                SpawnerRuneTooltips.append(event.getToolTip(), event.getItemStack(), levelFor(event));
            }

            @SubscribeEvent
            public void onRecipesUpdated(RecipesUpdatedEvent event) {
                SpawnerRuneTooltips.invalidate();
            }
        });
    }

    /**
     * Resolves the level the recipes should be read from.
     *
     * <h2>Why the tooltip context is not enough</h2>
     * A tooltip is gathered with one of two {@code Item.TooltipContext}s: {@code of(Level)}, which exposes a level, and
     * {@code of(HolderLookup.Provider)}, which does not. Vanilla screens use the first, but recipe viewers do not -
     * EMI builds its ingredient tooltips through the registries-only form, and the same is true of the startup search
     * tree. Taking {@code context.level()} alone therefore makes the rune stats vanish exactly where a player looks for
     * them (the EMI/JEI panel), which is what happened in testing.
     * <p>
     * The client level is the one that actually holds the synced recipe manager, so it is the correct fallback: the
     * tooltip context is only asked first so that a future caller passing an explicit level keeps control.
     *
     * @return the context's level when it has one, otherwise the client's current level, or null before a world loads
     */
    @Nullable
    private static Level levelFor(ItemTooltipEvent event) {
        Level fromContext = event.getContext().level();
        return fromContext != null ? fromContext : Minecraft.getInstance().level;
    }
}
