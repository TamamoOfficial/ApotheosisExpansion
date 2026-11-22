package com.tamamo.apotheosisexpansion.gemcompendium;

import java.util.ArrayList;
import java.util.List;

import com.tamamo.apotheosisexpansion.gemcompendium.GemCompendiumModel.Entry;

import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.GemBonus;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;

/**
 * Turns an {@link Entry} into the lines of text a viewer page shows.
 *
 * <h2>One page per gem, purities on one line</h2>
 * Each bonus becomes a single row whose values are the bonus at every purity the gem can exist at, separated by
 * "/", with a legend line above the table giving the purity order (cracked to perfect, only the purities at or
 * above the gem's minimum). A row whose bonus covers only some purities prints "-" for the ones it does not, so
 * the columns stay aligned and the gap is visible rather than silently shifted.
 *
 * <h2>Why the bonus lines go through Apotheosis itself</h2>
 * Each value is produced by {@link GemBonus#getSocketBonusTooltip}, the same call Apotheosis makes when it builds a
 * gem's own tooltip. Re-implementing the wording for attribute / enchantment / mob-effect / special bonuses would
 * mean duplicating a dozen formats and drifting from them, and it would not cover the bonuses other mods add.
 * <p>
 * That call wants an {@link AttributeTooltipContext}. One is built with a {@code null} player, because a page is
 * static content: there is no player to attribute a value to. None of the bonuses Apotheosis 8.9.0 ships reads
 * {@code player()} - they format the fixed numbers they hold - so this is exact for everything in the base mod, and
 * {@link #safeTooltip} keeps a misbehaving add-on bonus from taking a page down with it.
 *
 * <h2>Wrapping is the viewer's job</h2>
 * {@link #lines} returns logical lines; each viewer wraps them with its own {@link Font} at its own page width. This
 * class deliberately knows neither, so the same lines lay out correctly in JEI's scroll box and in EMI's paginated
 * text area.
 */
public final class GemCompendiumPages {

    /** JEI page body: the info category's proven size, with the slot row on top. */
    public static final int JEI_WIDTH = 170;
    public static final int JEI_HEIGHT = 125;
    /** JEI scroll box: full width below the slot row. The box wraps its own text. */
    public static final int JEI_SCROLL_Y = 22;

    /** EMI page body. */
    public static final int EMI_WIDTH = 162;
    /**
     * The tallest an EMI page may be.
     * <p>
     * A page is normally exactly as tall as its content; this cap only exists so a gem with an enormous bonus list
     * paginates instead of running off the recipe window.
     */
    public static final int EMI_MAX_HEIGHT = 150;
    /** EMI text starts directly under the gem slot row (the slot occupies y 2..20). */
    public static final int EMI_TEXT_Y = 22;
    /** EMI's text line pitch. */
    public static final int EMI_LINE_HEIGHT = 9;
    /** Wrap width inside the EMI page. */
    public static final int EMI_WRAP = 150;

    private static final ChatFormatting HEADING = ChatFormatting.GOLD;
    private static final ChatFormatting LABEL = ChatFormatting.GRAY;
    private static final ChatFormatting BODY = ChatFormatting.WHITE;

    private GemCompendiumPages() {}

    /** The stack a page is about: the gem at its lowest purity, the form a player first meets. */
    public static ItemStack stackFor(Entry entry) {
        return GemItem.createStack(entry.gem(), entry.minPurity());
    }

    /**
     * Every form of the gem this page describes: one stack per reachable purity, in the game's purity order.
     * <p>
     * The viewers show these in the page's ingredient slot, which cycles through them - the natural companion to a
     * page whose bonus rows list every purity side by side.
     */
    public static List<ItemStack> stacksFor(Entry entry) {
        List<ItemStack> out = new ArrayList<>();
        for (Purity purity : Purity.values()) {
            if (purity.isAtLeast(entry.minPurity())) {
                out.add(GemItem.createStack(entry.gem(), purity));
            }
        }
        return out;
    }

    /**
     * The logical lines of one page, in order.
     * <p>
     * Structure: the gem's own item name, then a "Drops From" block answering where the gem comes from - the question
     * the Patchouli entry leaves open - then the purity legend and one row per bonus, values at every reachable
     * purity separated by "/".
     */
    public static List<Component> lines(Entry entry) {
        List<Component> out = new ArrayList<>();

        // Identity line: the item name carries the purity colour Apotheosis assigns the minimum purity.
        out.add(stackFor(entry).getHoverName().copy().withStyle(ChatFormatting.BOLD));

        out.add(heading("text.apotheosis_expansion.gem_compendium.drops_from"));
        if (entry.dimensionRestricted()) {
            out.add(bullet(Component.translatable("text.apotheosis_expansion.gem_compendium.dimensions",
                joinDimensions(entry.dimensions()))));
        }
        else {
            out.add(bullet(Component.translatable("text.apotheosis_expansion.gem_compendium.any_dimension")));
        }
        if (entry.tiers().isEmpty()) {
            // A gem with no usable weights cannot be rolled at all; saying so beats an empty line.
            out.add(bullet(Component.translatable("text.apotheosis_expansion.gem_compendium.no_tiers")));
        }
        else if (entry.tiers().size() == WorldTier.values().length) {
            out.add(bullet(Component.translatable("text.apotheosis_expansion.gem_compendium.any_tier")));
        }
        else {
            out.add(bullet(Component.translatable("text.apotheosis_expansion.gem_compendium.tiers",
                joinTiers(entry.tiers()))));
        }
        // Restriction kinds other than dimensions exist (biomes, game stages). They are named so a page never
        // silently omits a gate the player cannot pass. HolderSet has no isEmpty(), so the size is what is asked.
        if (entry.gem().constraints().biomes().size() > 0) {
            out.add(bullet(Component.translatable("text.apotheosis_expansion.gem_compendium.biome_gate")));
        }
        if (!entry.gem().constraints().gameStages().isEmpty()) {
            out.add(bullet(Component.translatable("text.apotheosis_expansion.gem_compendium.stage_gate")));
        }
        if (entry.unique()) {
            // Apotheosis' own label for the flag, so the page says exactly what the gem's tooltip says.
            out.add(bullet(Component.translatable("text.apotheosis.unique").withStyle(ChatFormatting.AQUA)));
        }

        out.add(blank());

        // The purities this gem can exist at, in the game's own order: the legend for the "/" columns below.
        List<Purity> purities = reachablePurities(entry);
        out.add(heading("text.apotheosis_expansion.gem_compendium.bonuses_at", joinPurityLegend(purities)));

        AttributeTooltipContext ctx = tooltipContext();
        for (GemBonus bonus : entry.gem().getBonuses()) {
            if (bonus == null) {
                continue;
            }
            Component row = bonusLine(entry, bonus, purities, ctx);
            // A bonus covering none of this gem's purities has nothing to say; an empty line would just add a gap.
            if (!row.getString().isEmpty()) {
                out.add(row);
            }
        }

        while (!out.isEmpty() && out.get(out.size() - 1).getString().isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** The purities at or above the gem's minimum, in the game's own order. */
    private static List<Purity> reachablePurities(Entry entry) {
        List<Purity> out = new ArrayList<>();
        for (Purity purity : Purity.values()) {
            if (purity.isAtLeast(entry.minPurity())) {
                out.add(purity);
            }
        }
        return out;
    }

    /** "cracked / chipped / ..." in each purity's own colour, the column header for the table below. */
    private static Component joinPurityLegend(List<Purity> purities) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < purities.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(" / ").withStyle(LABEL));
            }
            Purity purity = purities.get(i);
            MutableComponent name = purity.toComponent();
            if (purity.getColor() != null) {
                name = name.withStyle(style -> style.withColor(purity.getColor()));
            }
            out.append(name);
        }
        return out;
    }

    /**
     * One bonus row: the shared wording once, the per-purity numbers between slashes.
     * <p>
     * A bonus tooltip is almost always "some fixed text around one number" - "+%s Attack Damage", "Ignore %s%%
     * Durability Loss" - so repeating the whole sentence per purity buries the actual comparison in six copies of
     * the same words. The row is therefore compressed: the longest common prefix and suffix of the purity values are
     * printed once, and only the differing middles are joined by " / ". A trailing sign in the common prefix ("+")
     * travels with each middle, so the row reads "+1 / +2 / +3.5 / ... Attack Damage" rather than "+1 / 2 / 3.5".
     * When every purity gives the same value there is nothing to compare and the row prints that value once, with no
     * slashes at all.
     * <p>
     * A purity the bonus does not cover prints "-", keeping the columns of the row aligned with the legend above;
     * shifting the remaining values left would silently attribute a value to the wrong purity.
     */
    private static Component bonusLine(Entry entry, GemBonus bonus, List<Purity> purities,
        AttributeTooltipContext ctx) {
        List<List<Run>> runsPerPurity = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int firstSupported = -1;
        for (Purity purity : purities) {
            if (!bonus.supports(purity)) {
                runsPerPurity.add(null);
                texts.add(null);
                continue;
            }
            Component value = safeTooltip(bonus, entry, purity, ctx);
            List<Run> runs = runs(value);
            StringBuilder plain = new StringBuilder();
            for (Run run : runs) {
                plain.append(run.text());
            }
            runsPerPurity.add(runs);
            texts.add(plain.toString());
            if (firstSupported < 0) {
                firstSupported = texts.size() - 1;
            }
        }
        // A bonus that covers none of this gem's purities has nothing to say; dropping the row beats a row of dashes.
        if (firstSupported < 0) {
            return Component.empty();
        }

        String prefix = trimPrefixDigits(commonPrefix(texts), texts);
        String suffix = trimSuffixDigits(commonSuffix(texts, prefix.length()), texts);
        // A trailing sign belongs to every number, not to the shared head: split it off so "+1 / +2" stays signed.
        String head = prefix;
        String sign = "";
        if (!head.isEmpty() && (head.endsWith("+") || head.endsWith("-"))) {
            sign = head.substring(head.length() - 1);
            head = head.substring(0, head.length() - 1);
        }

        // Identical middles mean the bonus does not scale with purity: print it once, slashes would add nothing.
        String firstMiddle = middle(texts.get(firstSupported), head.length() + sign.length(), suffix.length());
        boolean allSame = true;
        for (String text : texts) {
            if (text != null && !middle(text, head.length() + sign.length(), suffix.length()).equals(firstMiddle)) {
                allSame = false;
                break;
            }
        }

        List<Run> refRuns = runsPerPurity.get(firstSupported);
        String refText = texts.get(firstSupported);
        MutableComponent out = Component.empty();
        out.append(slice(refRuns, 0, head.length()));
        if (allSame) {
            out.append(slice(refRuns, head.length(), refText.length() - suffix.length()));
        }
        else {
            boolean first = true;
            for (int i = 0; i < purities.size(); i++) {
                if (!first) {
                    out.append(Component.literal(" / ").withStyle(LABEL));
                }
                first = false;
                if (texts.get(i) == null) {
                    out.append(Component.literal("-").withStyle(ChatFormatting.DARK_GRAY));
                }
                else {
                    out.append(slice(runsPerPurity.get(i), head.length(), texts.get(i).length() - suffix.length()));
                }
            }
        }
        out.append(slice(refRuns, refText.length() - suffix.length(), refText.length()));

        String classKey = bonus.getGemClass() == null ? "anything" : bonus.getGemClass().key();
        return bullet(Component.translatable("text.apotheosis_expansion.gem_compendium.bonus_line",
            Component.translatable(gemClassKeyOf(classKey)), out));
    }

    /** The part of a value between the shared head (plus sign) and the shared tail. */
    private static String middle(String text, int from, int suffixLength) {
        return text.substring(Math.min(from, text.length()), Math.max(from, text.length() - suffixLength));
    }

    /** Longest common prefix of every supported value. */
    private static String commonPrefix(List<String> texts) {
        String prefix = null;
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            if (prefix == null) {
                prefix = text;
                continue;
            }
            int len = Math.min(prefix.length(), text.length());
            int i = 0;
            while (i < len && prefix.charAt(i) == text.charAt(i)) {
                i++;
            }
            prefix = prefix.substring(0, i);
        }
        return prefix == null ? "" : prefix;
    }

    /**
     * Longest common suffix of every supported value, trimmed so it cannot reach into the common prefix.
     * <p>
     * Without the trim a value that is entirely shared ("+3.5" for every purity) would leave prefix and suffix
     * overlapping and the middle computation would invert.
     */
    private static String commonSuffix(List<String> texts, int prefixLength) {
        String suffix = null;
        int shortest = Integer.MAX_VALUE;
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            shortest = Math.min(shortest, text.length());
            if (suffix == null) {
                suffix = text;
                continue;
            }
            int a = suffix.length();
            int b = text.length();
            int i = 0;
            while (i < a && i < b && suffix.charAt(a - 1 - i) == text.charAt(b - 1 - i)) {
                i++;
            }
            suffix = suffix.substring(a - i);
        }
        if (suffix == null) {
            return "";
        }
        int max = Math.max(0, shortest - prefixLength);
        return suffix.length() > max ? suffix.substring(suffix.length() - max) : suffix;
    }

    /**
     * Pulls a shared prefix back to a word boundary when it ends inside a number.
     * <p>
     * "Ignore 10% Durability Loss" and "Ignore 15% ..." share the prefix "Ignore 1", which would split the first
     * value into "0" and "5%". Dropping the trailing digits makes the middles "10" and "15" instead - the whole
     * number is what a player compares.
     */
    private static String trimPrefixDigits(String prefix, List<String> texts) {
        String out = prefix;
        while (!out.isEmpty() && Character.isDigit(out.charAt(out.length() - 1))) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    /** The mirror of {@link #trimPrefixDigits} for the shared tail. */
    private static String trimSuffixDigits(String suffix, List<String> texts) {
        String out = suffix;
        while (!out.isEmpty() && Character.isDigit(out.charAt(0))) {
            out = out.substring(1);
        }
        return out;
    }

    /** A run of characters sharing one style: the unit prefix/suffix compression slices on. */
    private record Run(String text, net.minecraft.network.chat.Style style) {}

    /**
     * Decomposes a component into styled runs.
     * <p>
     * Going through the visual order keeps every colour the bonus formatter chose - the yellow value, the blue
     * attribute name - so compression trims text without flattening the tooltip's own styling.
     */
    private static List<Run> runs(Component component) {
        List<Run> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        net.minecraft.network.chat.Style[] last = new net.minecraft.network.chat.Style[1];
        net.minecraft.locale.Language.getInstance().getVisualOrder(component)
            .accept((index, style, codePoint) -> {
                if (last[0] != null && !last[0].equals(style)) {
                    out.add(new Run(current.toString(), last[0]));
                    current.setLength(0);
                }
                last[0] = style;
                current.appendCodePoint(codePoint);
                return true;
            });
        if (current.length() > 0) {
            out.add(new Run(current.toString(), last[0]));
        }
        return out;
    }

    /** Rebuilds the characters [from, to) of a run list as a component, keeping each run's style. */
    private static Component slice(List<Run> runs, int from, int to) {
        MutableComponent out = Component.empty();
        int pos = 0;
        for (Run run : runs) {
            int start = pos;
            int end = pos + run.text().length();
            if (end > from && start < to) {
                int a = Math.max(0, from - start);
                int b = Math.min(run.text().length(), to - start);
                out.append(Component.literal(run.text().substring(a, b)).withStyle(run.style()));
            }
            pos = end;
        }
        return out;
    }

    /** Wraps logical lines at the viewer's page width, preserving each line's style. */
    public static List<FormattedCharSequence> wrap(List<Component> lines, Font font, int width) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(font.split(line, width));
        }
        return out;
    }

    /**
     * One bonus value, defending against a bonus that throws.
     * <p>
     * The tooltip call is third-party code reached for every purity of every gem, and a page is cosmetic: if one
     * add-on's bonus throws, the value degrades to a marker rather than taking the viewer down with it.
     */
    private static Component safeTooltip(GemBonus bonus, Entry entry, Purity purity, AttributeTooltipContext ctx) {
        try {
            // The view must carry the purity being described, so the stack is built at exactly that purity:
            // Apotheosis' UnsocketedGem reads the purity back out of the stack it is given.
            UnsocketedGem view = UnsocketedGem.of(GemItem.createStack(entry.gem(), purity));
            Component text = bonus.getSocketBonusTooltip(view, ctx);
            return text == null ? Component.empty() : text;
        }
        catch (RuntimeException | LinkageError ex) {
            return Component.translatable("text.apotheosis_expansion.gem_compendium.bonus_error")
                .withStyle(ChatFormatting.RED);
        }
    }

    /**
     * The context every bonus formatter is given.
     * <p>
     * {@code TooltipFlag.NORMAL} keeps the output identical to a normal-detail tooltip. {@code TooltipContext.EMPTY}
     * is enough because the formatters format the values they already hold - nothing is resolved through the
     * registries lookup - and its {@code tickRate} is the vanilla 20, so cooldowns print in the same unit the item
     * tooltip uses.
     */
    private static AttributeTooltipContext tooltipContext() {
        return AttributeTooltipContext.of(null, Item.TooltipContext.EMPTY, TooltipFlag.NORMAL);
    }

    // ---------------------------------------------------------------- formatting helpers

    /** Comma-joined dimension names, each translated when a key exists and shown as an id when it does not. */
    public static Component joinDimensions(List<ResourceLocation> dimensions) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < dimensions.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(", ").withStyle(LABEL));
            }
            out.append(dimensionName(dimensions.get(i)));
        }
        return out;
    }

    /**
     * How a dimension is named on a page.
     * <p>
     * Vanilla ships no {@code dimension.*} translation keys at all, so this mod provides its own for the three
     * vanilla dimensions. A modded dimension falls back to its raw id, the only stable name it has - inventing a
     * pretty label for a dimension the player would not recognise would be worse.
     */
    public static Component dimensionName(ResourceLocation dimension) {
        String key = "dimension." + dimension.getNamespace() + "." + dimension.getPath();
        if (net.minecraft.locale.Language.getInstance().has(key)) {
            return Component.translatable(key).withStyle(BODY);
        }
        return Component.literal(dimension.toString()).withStyle(BODY);
    }

    /** Comma-joined tier names, using Apotheosis' own tier translations. */
    public static Component joinTiers(List<WorldTier> tiers) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < tiers.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(", ").withStyle(LABEL));
            }
            out.append(tiers.get(i).toComponent().withStyle(BODY));
        }
        return out;
    }

    /** Translation key for a socket-target key ("melee_weapon" -> "gem_class.melee_weapon"). */
    public static String gemClassKeyOf(String key) {
        return "gem_class." + key;
    }

    private static Component heading(String key, Object... args) {
        return Component.translatable(key, args).withStyle(HEADING);
    }

    /**
     * A bulleted line, using Apotheosis' own bullet prefix so the page reads like the gem's own tooltip.
     */
    private static Component bullet(Component text) {
        return Component.translatable("text.apotheosis.dot_prefix", text).withStyle(BODY);
    }

    private static Component blank() {
        return Component.empty();
    }
}
