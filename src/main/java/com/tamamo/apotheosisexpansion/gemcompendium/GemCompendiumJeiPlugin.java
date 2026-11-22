package com.tamamo.apotheosisexpansion.gemcompendium;

import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.tamamo.apotheosisexpansion.ApotheosisExpansion;
import com.tamamo.apotheosisexpansion.Config;
import com.tamamo.apotheosisexpansion.gemcompendium.GemCompendiumModel.Entry;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IScrollBoxWidget;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The JEI half of the gem compendium.
 *
 * <h2>Why a recipe category rather than ingredient info</h2>
 * {@code addIngredientInfo} is the obvious hook, but it files the page under JEI's own "Information" category, mixed
 * in with every other mod's blurbs. A dedicated category keeps the gem pages together, gives the category list a
 * recognisable entry, and lets the page put the gem in a real slot.
 *
 * <h2>The layout is JEI's own information page, reused</h2>
 * 170x125 with the ingredient slot on the top row and a scroll box filling the rest is exactly how JEI renders its
 * built-in information pages, so the page chrome matches what players already know and the scroll box - the only
 * scrolling text area JEI exposes to plugins - does the line layout.
 *
 * <h2>The EMI rule</h2>
 * {@link GemCompendiumGate#useJei()} is false when EMI is installed; every registration method then does nothing. The
 * class still loads, which it must - JEI instantiates every {@code @JeiPlugin} on the class path.
 *
 * <h2>Why {@code getRegistryName} is overridden</h2>
 * JEI's default builds a bookmark codec from the category's ingredient list, which a plain record cannot satisfy.
 * Returning the gem id gives JEI a stable identity per page, which is also what makes bookmarks work.
 */
@JeiPlugin
public final class GemCompendiumJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath(ApotheosisExpansion.MODID, "gem_compendium");

    /** The page type. The recipe object is the page model itself: it holds every fact the page shows. */
    public static final RecipeType<Entry> GEM_COMPENDIUM =
        RecipeType.create(ApotheosisExpansion.MODID, "gem_compendium", Entry.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!GemCompendiumGate.useJei()) {
            return;
        }
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!GemCompendiumGate.useJei() || !Config.enableGemCompendium()) {
            return;
        }
        List<Entry> entries = GemCompendiumModel.snapshotEntries();
        if (entries.isEmpty()) {
            // The gem registry is empty until the first data reload. Publishing nothing is correct: JEI re-runs this
            // after every reload, and the pages appear as soon as gems exist.
            LOGGER.debug("[ApotheosisExpansion] Gem compendium: no gems registered yet");
            return;
        }
        registration.addRecipes(GEM_COMPENDIUM, entries);
        LOGGER.info("[ApotheosisExpansion] Gem compendium: {} JEI page(s)", entries.size());
    }

    /**
     * No catalyst registration.
     * <p>
     * A catalyst is a workstation - the thing that enables the recipe, like a crafting table for crafting. There is
     * no station here: the page is reached because the gem stack itself is an ingredient of it (the visible input
     * slot and the invisible output in {@link Category#setRecipe}), which is exactly how JEI's own information
     * pages are found.
     */

    /** One page: the gem slot on top, the wrapped text in a scroll box below. */
    private static final class Category extends AbstractRecipeCategory<Entry> {

        /**
         * JEI's default {@code getBackground} returns null and its layout then throws, so the page area is supplied
         * explicitly. A blank drawable of the page size is exactly what JEI's own information category uses.
         */
        private final IDrawable background;

        private Category(mezz.jei.api.helpers.IGuiHelper guiHelper) {
            super(GEM_COMPENDIUM,
                Component.translatable("text.apotheosis_expansion.gem_compendium.category"),
                icon(guiHelper),
                GemCompendiumPages.JEI_WIDTH, GemCompendiumPages.JEI_HEIGHT);
            this.background = guiHelper.createBlankDrawable(
                GemCompendiumPages.JEI_WIDTH, GemCompendiumPages.JEI_HEIGHT);
        }

        @Override
        public IDrawable getBackground() {
            return this.background;
        }

        /**
         * The icon is a real gem stack.
         * <p>
         * Taken at registration time from whatever the registry holds; when it is still empty a blank drawable keeps
         * the category constructible, and JEI re-registers after the reload anyway.
         */
        private static IDrawable icon(mezz.jei.api.helpers.IGuiHelper guiHelper) {
            for (dev.shadowsoffire.apotheosis.socket.gem.Gem gem
                    : dev.shadowsoffire.apotheosis.socket.gem.GemRegistry.INSTANCE.getValues()) {
                return guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                    dev.shadowsoffire.apotheosis.socket.gem.GemItem.createStack(
                        gem, dev.shadowsoffire.apotheosis.socket.gem.Purity.NORMAL));
            }
            return guiHelper.createBlankDrawable(16, 16);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
            // Same slot position JEI's own information page uses, so the chrome lines up with what players know.
            // Every purity of the gem goes into the slot, so it cycles through the gem's forms while the rows below
            // list their values side by side.
            builder.addInputSlot(77, 1)
                .setStandardSlotBackground()
                .addItemStacks(GemCompendiumPages.stacksFor(entry));
            // An invisible output of the same stacks makes the page reachable from any purity's "uses" lookup.
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                .addItemStacks(GemCompendiumPages.stacksFor(entry));
        }

        @Override
        public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder, Entry entry,
            IFocusGroup focuses) {
            // JEI's scroll box wraps the lines itself (DrawableWrappedText), so the logical lines go in as-is and
            // keep their styles; the box width is the wrap width. The box wants List<FormattedText>; Component
            // implements FormattedText, but List<Component> does not widen to List<FormattedText>, so the copy is
            // typed at the target.
            List<net.minecraft.network.chat.FormattedText> lines =
                new java.util.ArrayList<>(GemCompendiumPages.lines(entry));
            IScrollBoxWidget box = builder.addScrollBoxWidget(
                0, GemCompendiumPages.JEI_SCROLL_Y, GemCompendiumPages.JEI_WIDTH,
                GemCompendiumPages.JEI_HEIGHT - GemCompendiumPages.JEI_SCROLL_Y);
            box.setContents(lines);
        }

        @Override
        public ResourceLocation getRegistryName(Entry entry) {
            return ResourceLocation.fromNamespaceAndPath(entry.id().getNamespace(),
                "gem_compendium/" + entry.id().getPath());
        }
    }
}
