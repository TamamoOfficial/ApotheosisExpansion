package com.tamamo.apotheosisexpansion.spawnerrune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.tamamo.apotheosisexpansion.Config;

import dev.shadowsoffire.apothic_spawners.ASObjects;
import dev.shadowsoffire.apothic_spawners.modifiers.SpawnerModifier;
import dev.shadowsoffire.apothic_spawners.modifiers.StatModifier;
import dev.shadowsoffire.apothic_spawners.stats.SpawnerStat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

/**
 * Adds the actual stat changes of a spawner rune to its item tooltip.
 *
 * <h2>Why the recipe is the source of truth</h2>
 * In current Apotheosis an upgrade rune is not described by code: the numbers live in the
 * {@code apothic_spawners:spawner_modifier} recipe whose {@code mainhand} matches the rune, and the JEI category
 * renders them by reading that same recipe list. Anything hard-coded here - or written into a lang file - would be a
 * second copy that silently disagrees the moment a data pack edits
 * {@code data/apothic_spawners/recipe/spawner_modifiers/} or {@code data/apotheosis/recipe/spawner_modifiers/}. So the
 * tooltip asks the client's {@link RecipeManager} for every modifier recipe this stack is the main hand of, and prints
 * the decoded {@link StatModifier} list. The displayed numbers therefore follow whatever data pack is loaded, including
 * runes added by other mods.
 *
 * <h2>Why off-hand variants are skipped</h2>
 * A pack may ship the same rune twice: once alone, once with an off-hand catalyst that reverses the effect (the
 * shipped {@code spawner_modifiers/_inverse/} files put the rune in the main hand and quartz in the off hand). Only the
 * main-hand-only form is described, because that is the form a player gets by right-clicking the rune by itself;
 * listing both would read as a self-contradicting tooltip.
 *
 * <h2>Caching</h2>
 * Tooltips are re-gathered every frame while an item is hovered, so the recipe scan is memoised per item. The memo is
 * dropped by {@link #invalidate()} whenever the client receives a new recipe set. Components are rebuilt from the
 * cached stat list on every call rather than cached themselves, because {@code formatValue} resolves some translations
 * eagerly.
 *
 * <h2>Optional dependency</h2>
 * Every reference to {@code dev.shadowsoffire.apothic_spawners} in this mod lives in this class, which is only loaded
 * after {@link SpawnerRuneIntegration#isAvailable()} confirmed the mod is installed.
 */
public final class SpawnerRuneTooltips {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Per-item memo of the stat changes that item applies.
     * <p>
     * Keyed by {@link Item} because the tooltip is per stack but the recipe match is per item: an ingredient cannot
     * discriminate two stacks that differ only in components, and no shipped rune does either.
     */
    private static final Map<Item, List<StatModifier<?>>> CACHE = new HashMap<>();

    /**
     * Bumped by {@link #invalidate()} so a test can assert the wiring is in place. The value is never consulted when
     * reading the cache: {@code CACHE.clear()} already makes every old entry unreachable, and unlike a per-chunk memo
     * there is no window where a stale entry could be read after the clear - the whole tooltip path runs on the client
     * thread and {@code invalidate()} is called from the same thread.
     */
    private static int generations = 0;

    private SpawnerRuneTooltips() {}

    /** Drops the memo, so the next tooltip re-reads the recipes. */
    public static void invalidate() {
        generations++;
        CACHE.clear();
    }

    /** Only for the validator: proves {@link #invalidate()} was actually wired to an event. */
    public static int generations() {
        return generations;
    }

    /**
     * Appends the description to a tooltip being gathered.
     * <p>
     * Vanilla posts the tooltip event at the very end of {@code ItemStack#getTooltipLines}, so these lines land after
     * the item's own description (the {@code item.<id>.desc} line that Apotheosis ships for every rune).
     *
     * @param tooltip the tooltip being built; appended to in place
     * @param stack   the stack whose tooltip is open
     * @param level   the client level, or null before a world is joined (the startup search-tree build)
     */
    public static void append(List<Component> tooltip, ItemStack stack, @Nullable Level level) {
        if (!Config.enableSpawnerRuneTooltips()) {
            return;
        }

        List<StatModifier<?>> changes = changesFor(stack, level);
        if (changes.isEmpty()) {
            return;
        }

        tooltip.add(Component.empty());
        for (StatModifier<?> change : changes) {
            tooltip.add(describe(change));
        }
    }

    /**
     * The stat changes this stack applies, memoised.
     * <p>
     * Returns an empty list rather than throwing when there is nothing to read: a null level means this is the startup
     * search-tree build or a dedicated server, and neither has recipes to consult.
     */
    private static List<StatModifier<?>> changesFor(ItemStack stack, @Nullable Level level) {
        if (level == null || stack.isEmpty()) {
            return List.of();
        }

        List<StatModifier<?>> cached = CACHE.get(stack.getItem());
        if (cached != null) {
            return cached;
        }

        List<StatModifier<?>> changes = scan(stack, level);
        CACHE.put(stack.getItem(), changes);
        return changes;
    }

    private static List<StatModifier<?>> scan(ItemStack stack, Level level) {
        RecipeManager recipes = level.getRecipeManager();
        if (recipes == null) {
            return List.of();
        }

        List<RecipeHolder<SpawnerModifier>> candidates;
        try {
            candidates = recipes.getAllRecipesFor(ASObjects.SPAWNER_MODIFIER.get());
        }
        catch (RuntimeException ex) {
            // The recipe manager is replaced during /reload; a tooltip gathered mid-swap simply shows nothing.
            LOGGER.warn("[ApotheosisExpansion] Could not read spawner modifier recipes; the rune tooltip is skipped", ex);
            return List.of();
        }

        List<StatModifier<?>> changes = new ArrayList<>();
        for (RecipeHolder<SpawnerModifier> holder : candidates) {
            SpawnerModifier modifier = holder.value();

            // Off-hand variants are the inverse recipes; the plain rune is the form a player applies alone.
            //
            // isEmpty() rather than `!= Ingredient.EMPTY`: the codec builds a fresh Ingredient for a missing field, so
            // an identity comparison against the EMPTY singleton can fail and would silently skip every recipe.
            if (!modifier.getOffhandInput().isEmpty()) {
                continue;
            }

            if (!matches(modifier, stack)) {
                continue;
            }

            changes.addAll(modifier.getStatModifiers());
        }

        LOGGER.debug("[ApotheosisExpansion] {} of {} spawner modifier recipes match {}",
            changes.size(), candidates.size(), stack.getItem());
        return List.copyOf(changes);
    }
    private static boolean matches(SpawnerModifier modifier, ItemStack stack) {
        try {
            return modifier.getMainhandInput().test(stack);
        }
        catch (RuntimeException ex) {
            // A tag-backed ingredient over an undefined tag throws while resolving; treat it as no match. Logged at
            // debug because a broken data pack would otherwise silently lose one rune's description.
            LOGGER.debug("[ApotheosisExpansion] Ingredient of a spawner modifier failed to resolve", ex);
            return false;
        }
    }

    /**
     * Formats one stat change.
     * <p>
     * The wording deliberately matches what the spawner block itself prints, so tooltip and block agree: a {@code set}
     * reads "Min Spawn Delay: 150", an {@code add} reads "+2 Spawn Range (max 32)". Stat names and value formatting
     * come from Apothic Spawners' own {@link SpawnerStat}, so translations and value units (ticks, percentages, roman
     * numerals for levels) are reused instead of duplicated here - which is also why a data pack that renames a stat or
     * changes its value type is displayed correctly with no code change.
     */
    private static Component describe(StatModifier<?> change) {
        SpawnerStat<?> stat = change.stat();
        String value = change.getFormattedValue();

        if (change.mode() == StatModifier.Mode.SET && change.value() instanceof Boolean on) {
            // A boolean stat is a state, not a number; the stat name alone is the whole line.
            return (on
                ? Component.translatable("apotheosis_expansion.spawner_rune.enables", stat.name())
                : Component.translatable("apotheosis_expansion.spawner_rune.disables", stat.name()))
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
        }

        if (change.mode() == StatModifier.Mode.SET) {
            return Component.translatable("apotheosis_expansion.spawner_rune.set", stat.name(), value)
                .withStyle(ChatFormatting.GREEN);
        }

        return additive(stat, change, value);
    }

    /**
     * Formats an additive change as {@code +N Name (max M)}.
     * <p>
     * The clamp is part of the real behaviour - the shipped spawn-range rune stops at 32 however often it is applied,
     * and the echoing rune stops at level 3 - so hiding it would make the tooltip less accurate than the recipe it is
     * derived from.
     */
    private static Component additive(SpawnerStat<?> stat, StatModifier<?> change, String value) {
        MutableComponent line = Component.empty();

        if (change.value() instanceof Number number && number.doubleValue() > 0) {
            line.append(Component.literal("+").withStyle(ChatFormatting.GREEN));
        }
        else if (change.value() instanceof Number number && number.doubleValue() < 0) {
            line.append(Component.literal("-").withStyle(ChatFormatting.RED));
            // formatValue already carries the sign for negative values (see LevelStat), so it is stripped here to
            // avoid printing a double minus.
            value = value.startsWith("-") ? value.substring(1) : value;
        }

        line.append(Component.literal(value).withStyle(ChatFormatting.GREEN));
        line.append(Component.literal(" "));
        line.append(stat.name().withStyle(ChatFormatting.GREEN));

        Component clamp = clamp(change);
        if (clamp != null) {
            line.append(Component.literal(" "));
            line.append(clamp);
        }

        return line;
    }

    @Nullable
    private static Component clamp(StatModifier<?> change) {
        Optional<?> min = change.min();
        Optional<?> max = change.max();
        if (min.isEmpty() && max.isEmpty()) {
            return null;
        }

        MutableComponent text = Component.empty();
        if (max.isPresent()) {
            text.append(Component.translatable("apotheosis_expansion.spawner_rune.max", format(change, max.get())));
        }
        if (min.isPresent()) {
            if (max.isPresent()) {
                text.append(Component.literal(" "));
            }
            text.append(Component.translatable("apotheosis_expansion.spawner_rune.min", format(change, min.get())));
        }
        return text.withStyle(ChatFormatting.GRAY);
    }

    /** Lets the stat render its own value type: ticks, percentages, roman level, {@code true}/{@code false}. */
    @SuppressWarnings("unchecked")
    private static String format(StatModifier<?> change, Object value) {
        return ((SpawnerStat<Object>) change.stat()).formatValue(value);
    }
}
