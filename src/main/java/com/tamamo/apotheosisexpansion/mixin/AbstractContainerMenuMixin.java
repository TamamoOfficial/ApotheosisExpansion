package com.tamamo.apotheosisexpansion.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.logging.LogUtils;
import com.tamamo.apotheosisexpansion.CraftedAffixRoller;
import com.tamamo.apotheosisexpansion.RepairCombineInheritance;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Applies Apotheosis affixes and gem sockets to items a player just crafted.
 * <p>
 * <b>Why {@code clicked} and not {@code ResultSlot.onTake}.</b> Crafting takes two different routes through
 * {@link AbstractContainerMenu#clicked}, and only one of them reaches {@code onTake}:
 * <ul>
 * <li><b>Pick the result up</b> — {@code clicked} → {@code slot.onTake(...)} → {@code ResultSlot.onTake}.
 * The stack that ends up on the cursor is taken from the result container <em>before</em> {@code onTake} runs.</li>
 * <li><b>Shift-click the result</b> — {@code clicked} → {@code QUICK_MOVE} → {@code quickMoveStack}, which pushes the
 * item straight into the inventory. {@code quickMoveStack} only calls {@code onQuickCraft}, never {@code onTake}.</li>
 * </ul>
 * An {@code onTake} hook would therefore silently miss every shift-click. Worse, the stack handed to
 * {@code ItemCraftedEvent} on the shift-click path is an intermediate {@code copy()} that is neither the stack in the
 * result container nor the stack inserted into the inventory, so mutating it changes nothing the player receives.
 * <p>
 * Instead this mixin brackets the whole {@code clicked} call: it snapshots the player inventory and cursor at
 * {@code HEAD} and diffs them at {@code TAIL}. Whatever <em>newly appeared</em> is the crafted item, already sitting in
 * the inventory or on the cursor — which is the "process it after it entered the inventory" behaviour that was asked
 * for. Nothing else can be touched: an item that was already in the inventory is present in the snapshot and is
 * therefore never a candidate, so existing gear can't be retroactively affixed by opening a crafting table.
 * <p>
 * {@code clicked} also runs on the client ({@code MultiPlayerGameMode} predicts the click), so every entry point
 * bails out unless we are on the logical server. Clients learn the result through normal container synchronisation.
 * <p>
 * <b>Combines are not crafts.</b> Before the click, the grid and the recipe that matches it are captured. When that
 * recipe is vanilla's repair-item combine the output is treated as derived from its inputs — data is inherited from
 * the best input, and a combine of two plain items stays plain instead of minting a fresh affixed item. Every other
 * recipe is a real craft and is rolled normally. See {@link RepairCombineInheritance}.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    /** Inventory contents before the click. Null when the click cannot possibly craft something. */
    @Unique
    @Nullable
    private ItemStack[] apotheosisExpansion$before;

    /** Cursor contents before the click. */
    @Unique
    @Nullable
    private ItemStack apotheosisExpansion$beforeCarried;

    /**
     * Snapshot of the crafting grid taken before the click, including the resolved recipe.
     * Used to detect the vanilla repair-combine recipe (two damaged tools -> one plain output) and inherit
     * the inputs' affixes instead of re-rolling them - and to leave every other recipe on the normal path.
     */
    @Unique
    @Nullable
    private RepairCombineInheritance.GridSnapshot apotheosisExpansion$gridSnapshot;

    @Inject(method = "clicked", at = @At("HEAD"))
    private void apotheosisExpansion$captureBefore(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        this.apotheosisExpansion$before = null;
        this.apotheosisExpansion$beforeCarried = null;
        this.apotheosisExpansion$gridSnapshot = null;

        if (!apotheosisExpansion$isServerCraftingResultClick((AbstractContainerMenu) (Object) this, slotId, player)) {
            return;
        }

        Inventory inv = player.getInventory();
        ItemStack[] snapshot = new ItemStack[inv.getContainerSize()];
        for (int i = 0; i < snapshot.length; i++) {
            snapshot[i] = inv.getItem(i).copy();
        }
        this.apotheosisExpansion$before = snapshot;
        this.apotheosisExpansion$beforeCarried = ((AbstractContainerMenu) (Object) this).getCarried().copy();

        // Capture the grid before the recipe consumes it. The grid slots are exactly those whose container is a
        // CraftingContainer - no reflection into ResultSlot.craftSlots needed, and modded tables that build their
        // menus the vanilla way are covered automatically.
        for (Slot slot : ((AbstractContainerMenu) (Object) this).slots) {
            if (slot.container instanceof net.minecraft.world.inventory.CraftingContainer grid) {
                this.apotheosisExpansion$gridSnapshot = RepairCombineInheritance.snapshot(player.level(), grid);
                break;
            }
        }
    }

    @Inject(method = "clicked", at = @At("TAIL"))
    private void apotheosisExpansion$decorateAfter(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        ItemStack[] before = this.apotheosisExpansion$before;
        ItemStack beforeCarried = this.apotheosisExpansion$beforeCarried;
        RepairCombineInheritance.GridSnapshot grid = this.apotheosisExpansion$gridSnapshot;

        // No snapshot means the HEAD hook decided this click cannot craft anything.
        if (before == null || beforeCarried == null) {
            return;
        }
        this.apotheosisExpansion$before = null;
        this.apotheosisExpansion$beforeCarried = null;
        this.apotheosisExpansion$gridSnapshot = null;

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        try {
            Inventory inv = player.getInventory();

            // Normal click: the crafted item is on the cursor.
            ItemStack carried = menu.getCarried();
            if (apotheosisExpansion$isNewlyAppeared(beforeCarried, carried)) {
                if (apotheosisExpansion$decorate(carried, player, grid)) {
                    menu.setCarried(carried);
                }
                return;
            }

            // Shift-click: one or more crafted items were pushed into the inventory. Each successful shift-click
            // iteration produces exactly one item, so several slots may have gained a stack in a single call.
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack after = inv.getItem(i);
                if (after.isEmpty()) {
                    continue;
                }
                ItemStack prior = i < before.length ? before[i] : ItemStack.EMPTY;
                if (!apotheosisExpansion$isNewlyAppeared(prior, after)) {
                    continue;
                }
                if (apotheosisExpansion$decorate(after, player, grid)) {
                    inv.setItem(i, after);
                }
            }
        }
        catch (Exception ex) {
            // A generation failure must never break the crafting interaction.
            LogUtils.getLogger().error("[ApotheosisExpansion] Failed to apply affixes to a crafted item", ex);
        }
    }

    /**
     * Applies the right treatment to one freshly produced stack.
     * <p>
     * <b>Repair combines are never rolled.</b> When the click ran vanilla's repair-item recipe the output is
     * <em>derived from</em> its inputs, not newly crafted:
     * <ul>
     * <li>an input carried Apotheosis data — copy the best input's data onto the output;</li>
     * <li>no input carried any — leave the output plain.</li>
     * </ul>
     * The second case is the loophole fix: rolling there made two cheap plain tools mint affixed gear on every
     * merge, turning the repair grid into a free affix generator that sidesteps
     * {@code skip_if_already_affixed} entirely.
     * <p>
     * Every other recipe is a genuine craft and goes through the normal roll, which still honours
     * {@code skip_if_already_affixed} for inputs that legitimately keep their components.
     *
     * @return true when the stack was modified and must be written back to its slot
     */
    @Unique
    private static boolean apotheosisExpansion$decorate(ItemStack stack, Player player, @Nullable RepairCombineInheritance.GridSnapshot grid) {
        if (RepairCombineInheritance.tryInherit(stack, grid)) {
            return true;
        }
        if (RepairCombineInheritance.isNonRollingCombine(grid)) {
            return false;
        }
        return CraftedAffixRoller.tryApply(stack, player);
    }

    /**
     * True when {@code after} looks like a stack that was added by this click rather than one that was already there.
     * <p>
     * Ingredient consumption (counts going down, stacks becoming empty) and unrelated slots must not match, so the
     * check requires the slot to hold something now and either have been empty before or hold strictly more items of
     * the same kind.
     */
    @Unique
    private static boolean apotheosisExpansion$isNewlyAppeared(ItemStack before, ItemStack after) {
        if (after.isEmpty()) {
            return false;
        }
        if (before.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(before, after) && after.getCount() > before.getCount();
    }

    /**
     * Shared guard for both hooks: server side, valid player, and the clicked slot is a crafting result slot.
     * <p>
     * Checking the slot type instead of hard-coding menu classes means modded crafting tables that reuse
     * {@link ResultSlot} are supported, while every other menu (chests, furnaces, anvils, ...) is skipped for free.
     */
    @Unique
    private static boolean apotheosisExpansion$isServerCraftingResultClick(AbstractContainerMenu menu, int slotId, @Nullable Player player) {
        if (player == null || player.level().isClientSide()) {
            return false;
        }
        if (slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }
        Slot slot = menu.slots.get(slotId);
        return slot instanceof ResultSlot;
    }
}
