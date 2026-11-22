package com.tamamo.apotheosisexpansion.gemcompendium;

import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.tamamo.apotheosisexpansion.ApotheosisExpansion;
import com.tamamo.apotheosisexpansion.Config;
import com.tamamo.apotheosisexpansion.gemcompendium.GemCompendiumModel.Entry;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The EMI half of the gem compendium.
 *
 * <h2>Why EMI always wins over JEI</h2>
 * See {@link GemCompendiumGate}. In short: EMI's JEMI bridge imports JEI plugins, so registering here whenever EMI
 * exists - and suppressing the JEI half in that case - is what keeps each page from appearing twice.
 *
 * <h2>Why the pages are rebuilt from the registry on every reload</h2>
 * EMI re-runs {@code register} after each resource reload (and after each JEI reload, through JEMI). Reading
 * {@link GemCompendiumModel#snapshotEntries()} at that moment means a page always reflects the data pack currently
 * loaded, including another mod's gems and this mod's own weight tweaks, with no cache to invalidate.
 *
 * <h2>Pagination</h2>
 * EMI has no scrolling text area, so a long page is split the way EMI's own information pages split theirs: a fixed
 * number of lines per page and the same prev/next buttons, texture coordinates included. The buttons are stateful
 * widgets, so the page index lives in a small holder object created per recipe display.
 */
@EmiEntrypoint
public final class GemCompendiumEmiPlugin implements EmiPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The category id. EMI derives its display name from {@code emi.category.<namespace>.<path>}. */
    public static final ResourceLocation CATEGORY_ID =
        ResourceLocation.fromNamespaceAndPath(ApotheosisExpansion.MODID, "gem_compendium");

    @Override
    public void register(EmiRegistry registry) {
        if (!GemCompendiumGate.useEmi() || !Config.enableGemCompendium()) {
            return;
        }

        List<Entry> entries = GemCompendiumModel.snapshotEntries();
        if (entries.isEmpty()) {
            // Nothing registered yet (before the first data reload). Registering no category at all is deliberate: an
            // empty category looks broken, while an absent one is simply "not loaded yet", and EMI asks again after
            // every reload.
            LOGGER.debug("[ApotheosisExpansion] Gem compendium: no gems registered yet");
            return;
        }

        // The icon is the first gem: EMI wants an EmiRenderable, and a gem stack renders exactly like it does in the
        // ingredient list. No workstation is registered - the gem is the page's own output, not a machine that
        // enables it - and EMI finds pages through that output link, so a "uses" lookup on a gem opens its page.
        EmiRecipeCategory category = new EmiRecipeCategory(CATEGORY_ID,
            EmiStack.of(GemCompendiumPages.stackFor(entries.get(0))));
        registry.addCategory(category);

        for (Entry entry : entries) {
            registry.addRecipe(new GemPage(category, entry));
        }
        LOGGER.info("[ApotheosisExpansion] Gem compendium: {} EMI page(s)", entries.size());
    }

    /** One gem's page: every purity's values on one line each, separated by "/". */
    private static final class GemPage extends BasicEmiRecipe {

        /**
         * Wrapped once, at construction.
         * <p>
         * EMI asks {@code getDisplayHeight} before {@code addWidgets}, so the layout must be known up front. Wrapping
         * here also pays the cost once per page per reload instead of once per frame while the page is on screen.
         * This runs on EMI's reload thread, exactly like the wrapping inside EMI's own {@code EmiInfoRecipe}.
         */
        private final List<FormattedCharSequence> lines;

        /** Lines that fit on one EMI page, given the text area below the slot row. */
        private final int linesPerPage;

        private GemPage(EmiRecipeCategory category, Entry entry) {
            // The page is exactly as tall as its content: a fixed height left a large empty band under short pages.
            // The cap only exists so an enormous bonus list paginates instead of running off the recipe window.
            super(category, pageId(entry), GemCompendiumPages.EMI_WIDTH, GemCompendiumPages.EMI_MAX_HEIGHT);
            // One stack per reachable purity, so the page's slot cycles through the gem's forms while the rows below
            // list their values side by side.
            this.outputs = GemCompendiumPages.stacksFor(entry).stream().map(EmiStack::of).toList();

            var font = Minecraft.getInstance().font;
            this.lines = GemCompendiumPages.wrap(GemCompendiumPages.lines(entry), font, GemCompendiumPages.EMI_WRAP);
            int textHeight = GemCompendiumPages.EMI_MAX_HEIGHT - GemCompendiumPages.EMI_TEXT_Y - 4;
            this.linesPerPage = Math.max(1, textHeight / GemCompendiumPages.EMI_LINE_HEIGHT);

            // Shrink the page to what it actually shows: the text block ends at TEXT_Y + lines-on-first-page * pitch.
            int used = GemCompendiumPages.EMI_TEXT_Y
                + Math.min(this.lines.size(), this.linesPerPage) * GemCompendiumPages.EMI_LINE_HEIGHT + 4;
            this.height = Math.min(GemCompendiumPages.EMI_MAX_HEIGHT, Math.max(used, 24));
        }

        /** A stable, viewer-unique id per gem, so EMI can deduplicate and bookmark the page. */
        private static ResourceLocation pageId(Entry entry) {
            return ResourceLocation.fromNamespaceAndPath(entry.id().getNamespace(),
                "gem_compendium/" + entry.id().getPath());
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            // The gem forms, top-left. The slot cycles through the outputs, so a page shows every purity of its gem.
            widgets.addSlot(EmiIngredient.of(this.outputs), 2, 2).recipeContext(this);

            Pager pager = new Pager(this.lines, this.linesPerPage);
            if (pager.pages() > 1) {
                // Top-right corner: the top-left is the ingredient slot, and EMI's own (2, 2) button position would
                // sit right on top of it. Same texture cells as EMI's own information pages.
                widgets.addButton(GemCompendiumPages.EMI_WIDTH - 26, 2, 12, 12, 0, 0, pager::hasPrev, pager::prev);
                widgets.addButton(GemCompendiumPages.EMI_WIDTH - 14, 2, 12, 12, 12, 0, pager::hasNext, pager::next);
            }
            widgets.addDrawable(0, GemCompendiumPages.EMI_TEXT_Y, GemCompendiumPages.EMI_WIDTH,
                this.height - GemCompendiumPages.EMI_TEXT_Y, pager::draw);
        }

        /** The page index of one displayed page, plus the two buttons that move it. */
        private static final class Pager {

            private final List<FormattedCharSequence> lines;
            private final int perPage;
            private int index;

            private Pager(List<FormattedCharSequence> lines, int perPage) {
                this.lines = lines;
                this.perPage = perPage;
            }

            private int pages() {
                return Math.max(1, (this.lines.size() + this.perPage - 1) / this.perPage);
            }

            private boolean hasPrev() {
                return this.index > 0;
            }

            private boolean hasNext() {
                return this.index + 1 < pages();
            }

            private void prev(double mouseX, double mouseY, int button) {
                if (hasPrev()) {
                    this.index--;
                }
            }

            private void next(double mouseX, double mouseY, int button) {
                if (hasNext()) {
                    this.index++;
                }
            }

            /**
             * Draws this page's slice of lines into the text area.
             * <p>
             * The two int arguments are the <em>mouse</em> position, not a drawing origin: DrawableWidget translates
             * the matrix to the widget's own (x, y) before invoking the consumer, so everything here is drawn in
             * widget-local coordinates. EMI's own information pages ignore the mouse arguments the same way - using
             * them as an origin makes the whole text block follow the cursor.
             */
            private void draw(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                // GuiGraphics directly: EMI's EmiDrawContext helper lives outside the api jar, and drawString is all
                // the page needs. Same colour and no shadow as EMI's own information pages.
                var font = Minecraft.getInstance().font;
                int start = this.index * this.perPage;
                int end = Math.min(this.lines.size(), start + this.perPage);
                int lineY = 0;
                for (int i = start; i < end; i++) {
                    graphics.drawString(font, this.lines.get(i), 4, lineY, 0xFFFFFF, false);
                    lineY += GemCompendiumPages.EMI_LINE_HEIGHT;
                }
            }
        }
    }
}
