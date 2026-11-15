package com.tamamo.apotheosisexpansion;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import dev.shadowsoffire.apotheosis.Apoth.Components;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.ItemAffixes;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

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
 * <b>The fix.</b> Before the click is processed, the crafting grid is snapshotted. If the output is the same item as
 * grid inputs that carried Apotheosis data, the most valuable input's affixes, rarity, sockets and name are copied
 * onto the output instead of rolling. Gated by {@link Config#inheritOnRepairCombine()}.
 * <p>
 * "Most valuable" = highest {@code sortIndex} rarity, ties broken by socket count, then by affix count. This mirrors
 * how the vanilla recipe itself picks (it takes the first of the pair for the name argument but the max of the
 * durabilities), except we deliberately favour the better item rather than the earlier slot.
 */
public final class RepairCombineInheritance {

    private RepairCombineInheritance() {}

    /**
     * Snapshot of the crafting grid taken before the click resolves.
     * Only stacks with Apotheosis data are kept; plain stacks are not inheritance sources.
     */
    public static List<ItemStack> snapshotApothGridItems(CraftingContainer grid) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (!stack.isEmpty() && CraftedAffixRoller.hasExistingApothData(stack)) {
                out.add(stack.copy());
            }
        }
        return out;
    }

    /**
     * If {@code output} is the result of combining grid inputs that had Apotheosis data, copies the best input's
     * affixes/rarity/sockets/gem data/name onto it.
     *
     * @param output  the freshly crafted stack (already in inventory or on cursor)
     * @param sources grid stacks captured before the click, already filtered to Apotheosis-bearing ones
     * @return true when data was inherited (the caller must then skip random generation)
     */
    public static boolean tryInherit(ItemStack output, @Nullable List<ItemStack> sources) {
        if (!Config.inheritOnRepairCombine() || output.isEmpty() || sources == null || sources.isEmpty()) {
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
        for (ItemStack source : sources) {
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
