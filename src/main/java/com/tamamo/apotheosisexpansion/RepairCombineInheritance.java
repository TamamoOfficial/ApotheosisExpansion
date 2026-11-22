package com.tamamo.apotheosisexpansion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import dev.shadowsoffire.apotheosis.Apoth.Components;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.ItemAffixes;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.Level;

/**
 * Handles the vanilla "repair item" combine recipe (two damaged tools of the same material in the crafting grid).
 * <p>
 * <b>The bug this fixes.</b> {@code RepairItemRecipe#assemble} builds a brand-new {@code ItemStack(item)} and copies
 * over only max-damage/damage and curse enchantments. Every Apotheosis component (affixes, rarity, sockets, gems,
 * affix name) is dropped, so the output looks like a plain unaffixed tool. The generation pipeline then treats it as a
 * freshly crafted item — {@code skip_if_already_affixed} does not protect it because the output genuinely has no
 * affixes — and rolls a brand new random set. Combining two fully-geared pickaxes therefore <em>re-rolls</em> their
 * affixes and socket counts, which is exactly the reported bug.
 * <p>
 * <b>The fix.</b> Before the click is processed, the crafting grid is snapshotted and the matching recipe is resolved.
 * When that recipe is {@link RepairItemRecipe}, the output is treated as <em>derived from its inputs</em>, never as a
 * new craft:
 * <ul>
 * <li>children with Apotheosis data → the most valuable input's affixes, rarity, sockets and name are copied onto the
 * output;</li>
 * <li>children without any Apotheosis data → the output stays plain. It is <b>not</b> rolled.</li>
 * </ul>
 * Gated by {@link Config#inheritOnRepairCombine()} for the <em>inheriting</em> half; the "do not roll" half is
 * unconditional (see {@link #isNonRollingCombine}).
 * <p>
 * <b>Why the second bullet is load-bearing (the reported loophole).</b> Treating the combine output as a fresh craft
 * means two plain damaged tools — which cost nothing to obtain — mint an affixed item on every merge. That turns the
 * repair recipe into an affix generator and bypasses every guard that exists to stop the crafting grid from being
 * farmed for affixes. A repair must preserve value, never create it.
 * <p>
 * <b>Why the recipe is resolved instead of guessed.</b> An earlier heuristic only asked "is the output the same item
 * as an affixed grid input?". That also matches a player laying two tool-shaped inputs next to each other and getting
 * some <em>other</em> recipe to fire, so affixes could leak from consumed ingredients onto a genuinely new craft. Only
 * the recipe that actually matched decides now; anything other than {@link RepairItemRecipe} goes through the normal
 * crafted-item pipeline and is subject to {@code skip_if_already_affixed} like before.
 * <p>
 * "Most valuable" = highest {@code sortIndex} rarity, ties broken by socket count, then by affix count. This mirrors
 * how the vanilla recipe itself picks (it takes the first of the pair for the name argument but the max of the
 * durabilities), except we deliberately favour the better item rather than the earlier slot.
 */
public final class RepairCombineInheritance {

    private RepairCombineInheritance() {}

    /**
     * Immutable view of the crafting grid taken before the click resolves.
     * <p>
     * Holds both the raw grid contents (needed to resolve the recipe before vanilla consumes them) and the already
     * filtered list of Apotheosis-bearing stacks, so the {@code TAIL} side never has to re-read a grid that is by
     * then empty.
     */
    public record GridSnapshot(List<ItemStack> sources, boolean repairCombine) {

        public GridSnapshot {
            sources = List.copyOf(sources);
        }
    }

    /**
     * Snapshots the grid and resolves which recipe is about to run.
     *
     * @param level the level the crafting menu belongs to (server side)
     * @param grid  the crafting grid, still untouched
     * @return the snapshot, or {@code null} when the grid is empty
     */
    @Nullable
    public static GridSnapshot snapshot(Level level, CraftingContainer grid) {
        List<ItemStack> sources = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            any = true;
            if (CraftedAffixRoller.hasExistingApothData(stack)) {
                sources.add(stack.copy());
            }
        }
        if (!any) {
            return null;
        }
        return new GridSnapshot(sources, isRepairCombine(level, grid));
    }

    /**
     * True when the recipe matching the current grid is vanilla's repair-item combine.
     * <p>
     * Resolved from the live {@code RecipeManager} rather than inferred from the item ids: this is the only recipe
     * that consumes both inputs and rebuilds a plain stack, which is precisely the situation where inheriting is
     * correct. Every other recipe (shaped, shapeless, modded, or another special recipe) is a genuine craft.
     */
    public static boolean isRepairCombine(Level level, CraftingContainer grid) {
        if (level.isClientSide()) {
            return false;
        }
        Optional<RecipeHolder<CraftingRecipe>> match = level.getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), level);
        return match.isPresent() && match.get().value() instanceof RepairItemRecipe;
    }

    /**
     * True when the output of this click must not be rolled at all, only inherited or left plain.
     * <p>
     * Deliberately <b>not</b> gated by {@link Config#inheritOnRepairCombine()}: the toggle decides whether the
     * inputs' affixes are carried over, but "a combine must never mint a fresh roll" is an exploit fix, and an
     * exploit fix that a player can switch off is not a fix. Two plain damaged tools are cheap; letting their merge
     * roll affixes turns the repair grid into a generator that sidesteps {@code skip_if_already_affixed} entirely.
     *
     * @return true when the caller must skip random generation unconditionally
     */
    public static boolean isNonRollingCombine(@Nullable GridSnapshot snapshot) {
        return snapshot != null && snapshot.repairCombine();
    }

    /**
     * If {@code output} is the result of combining grid inputs that had Apotheosis data, copies the best input's
     * affixes/rarity/sockets/gem data/name onto it.
     *
     * @param output   the freshly crafted stack (already in inventory or on cursor)
     * @param snapshot grid state captured before the click
     * @return true when data was inherited; false means the output carries no inherited data and, for a repair
     *         combine, must be left plain
     */
    public static boolean tryInherit(ItemStack output, @Nullable GridSnapshot snapshot) {
        if (!Config.inheritOnRepairCombine() || output.isEmpty() || snapshot == null) {
            return false;
        }
        // Only an actual combine copies inputs; anything else is a real craft and must keep its own result.
        if (!snapshot.repairCombine()) {
            return false;
        }
        // Only repair what was lost: if the recipe already carried the components over (banner/shield-style copies),
        // leave its own result untouched and let the normal skip rule handle it.
        if (CraftedAffixRoller.hasExistingApothData(output)) {
            return false;
        }

        // The vanilla combine recipe requires both inputs to be the same item; the output always matches them.
        // Requiring a same-item source with affix data makes this fire only for combines, never for ordinary
        // crafting of the same item from raw materials (a plain pickaxe crafted from sticks has no such source).
        ItemStack best = null;
        for (ItemStack source : snapshot.sources()) {
            if (source.isEmpty() || source.getItem() != output.getItem()) {
                continue;
            }
            if (best == null || isBetter(source, best)) {
                best = source;
            }
        }
        if (best == null) {
            return false;
        }

        copyApothData(best, output);
        return true;
    }

    /** Copies every Apotheosis component plus the affix name from source onto target. */
    private static void copyApothData(ItemStack source, ItemStack target) {
        ItemAffixes affixes = source.getOrDefault(Components.AFFIXES, ItemAffixes.EMPTY);
        if (!affixes.isEmpty()) {
            target.set(Components.AFFIXES, affixes);
        }
        else {
            target.remove(Components.AFFIXES);
        }

        DynamicHolder<LootRarity> rarity = AffixHelper.getRarity(source);
        if (rarity.isBound()) {
            target.set(Components.RARITY, rarity);
        }
        else {
            target.remove(Components.RARITY);
        }

        int sockets = source.getOrDefault(Components.SOCKETS, 0);
        if (sockets > 0) {
            target.set(Components.SOCKETS, sockets);
        }
        else {
            target.remove(Components.SOCKETS);
        }

        copyIfPresent(source, target, Components.SOCKETED_GEMS);
        copyIfPresent(source, target, Components.DURABILITY_BONUS);
        copyIfPresent(source, target, Components.TOUCHED_BY_MALICE);

        Component name = AffixHelper.getName(source);
        if (name != null) {
            // The stored affix-name component is a translatable shell whose base-name argument slot is an empty
            // placeholder: Apotheosis' ItemStackMixin re-fills it from the *queried* stack's hover name at render
            // time (getModifiedStackName works on a deep copy of the contents). Copying it verbatim therefore binds
            // the name to the target, not the source - no rebuild needed.
            AffixHelper.setName(target, name.copy());
        }
        else {
            target.remove(Components.AFFIX_NAME);
        }
    }

    private static <T> void copyIfPresent(ItemStack source, ItemStack target, net.minecraft.core.component.DataComponentType<T> type) {
        T value = source.get(type);
        if (value != null) {
            target.set(type, value);
        }
        else {
            target.remove(type);
        }
    }

    /** Ordering: rarity sort_index first, then socket count, then affix count. */
    private static boolean isBetter(ItemStack a, ItemStack b) {
        int ra = rarityIndex(a);
        int rb = rarityIndex(b);
        if (ra != rb) {
            return ra > rb;
        }
        int sa = a.getOrDefault(Components.SOCKETS, 0);
        int sb = b.getOrDefault(Components.SOCKETS, 0);
        if (sa != sb) {
            return sa > sb;
        }
        return a.getOrDefault(Components.AFFIXES, ItemAffixes.EMPTY).size()
            > b.getOrDefault(Components.AFFIXES, ItemAffixes.EMPTY).size();
    }

    private static int rarityIndex(ItemStack stack) {
        DynamicHolder<LootRarity> rarity = AffixHelper.getRarity(stack);
        return rarity.isBound() ? rarity.get().sortIndex() : -1;
    }
}
