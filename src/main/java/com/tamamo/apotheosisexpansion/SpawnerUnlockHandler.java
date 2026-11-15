package com.tamamo.apotheosisexpansion;

import java.util.List;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Re-allows specific spawn eggs on mob spawners, undoing a restriction imposed by Apotheosis / Apothic Spawners.
 * <p>
 * Each mob has its own config toggle, so a pack can unlock the Warden without unlocking the Elder Guardian (or the
 * other way round). Ported from the standalone Warden Spawner Unlock mod and extended to cover the Elder Guardian.
 *
 * <p><b>Where the restriction comes from.</b> On MC 1.21.1 Apothic Spawners cancels
 * {@code PlayerInteractEvent.RightClickBlock} at {@code NORMAL} priority whenever the held spawn egg's entity type
 * carries the {@code apothic_spawners:blacklisted_from_spawners} tag. That tag ships with:
 * <pre>
 *   minecraft:warden
 *   minecraft:elder_guardian
 *   #c:bosses
 * </pre>
 * It also appends a "banned" tooltip line to the egg.
 *
 * <p><b>How this is undone.</b> The same event is handled at {@code LOWEST} priority with
 * {@code receiveCanceled = true}, so it runs after Apothic Spawners and can revoke the cancellation — but only for a
 * spawn egg whose type is explicitly enabled in the config, and only when aimed at a spawner block. Every other
 * blacklisted entry (the {@code #c:bosses} tag, mod-added bosses, ...) stays refused, and no Apotheosis config file or
 * data pack is modified, so upgrading Apotheosis does not break this.
 *
 * <p><b>Why the interaction gates need resetting.</b> Cancelling {@code RightClickBlock} also forces its
 * {@code useBlock} / {@code useItem} gates to {@code FALSE}; clearing the cancelled flag alone does not restore them,
 * and the vanilla interaction pipeline checks those gates before dispatching to {@code SpawnEggItem#useOn}. Both are
 * explicitly returned to {@code DEFAULT}.
 */
public final class SpawnerUnlockHandler {

    private static final String[] BANNED_KEYS = {
        "misc.apotheosis.banned",
        "misc.apothic_spawners.banned",
    };

    /**
     * Re-opens the spawner interaction that Apotheosis cancelled, for the enabled spawn egg types.
     * <p>
     * Kept at {@code LOWEST} + {@code receiveCanceled = true}: NeoForge's event bus filters out already-cancelled
     * events for listeners that did not opt in, so without both settings this would never fire.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled()) {
            return;
        }
        if (!shouldUnlock(event.getLevel(), event.getPos(), event.getItemStack())) {
            return;
        }

        event.setCanceled(false);
        event.setUseBlock(TriState.DEFAULT);
        event.setUseItem(TriState.DEFAULT);
    }

    /**
     * Removes the "Cannot be used on a Spawner" line Apotheosis appends to an egg we have unlocked.
     * <p>
     * The line is added at {@code NORMAL} priority, so {@code LOWEST} runs after it. Matching is done on the
     * translation key rather than the rendered text so it works in every language.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemTooltip(ItemTooltipEvent event) {
        removeBannedLine(event.getItemStack(), event.getToolTip());
    }

    // ---- Shared predicates ----------------------------------------------

    /**
     * True when the held stack is a spawn egg for a mob the config has unlocked, and the targeted block is a spawner.
     * <p>
     * Apotheosis swaps in its own {@code ApothSpawnerBlock} / {@code ApothSpawnerTile}, which extend
     * {@link SpawnerBlock} / {@link SpawnerBlockEntity}, so both vanilla and Apotheosis spawners match.
     */
    static boolean shouldUnlock(Level level, BlockPos pos, ItemStack stack) {
        if (level == null || pos == null || stack.isEmpty()) {
            return false;
        }
        if (!isUnlockedEgg(stack)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SpawnerBlock)) {
            return false;
        }
        return level.getBlockEntity(pos) instanceof SpawnerBlockEntity;
    }

    /**
     * Resolves the egg's entity type and asks the config whether that specific mob is unlocked.
     * <p>
     * {@link SpawnEggItem#getType(ItemStack)} is used rather than comparing against {@code Items.WARDEN_SPAWN_EGG} and
     * friends so that modded/deferred spawn eggs and eggs carrying {@code minecraft:entity_data} are handled the same
     * way Apothic Spawners itself handles them. It is also what makes the {@code #c:bosses} tag entries keep working:
     * anything not explicitly enabled here simply stays refused.
     */
    static boolean isUnlockedEgg(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SpawnEggItem egg)) {
            return false;
        }
        EntityType<?> type = egg.getType(stack);
        if (type == null) {
            return false;
        }
        return isUnlockedType(type);
    }

    /** Maps an entity type onto its config toggle. Unknown types are never unlocked. */
    static boolean isUnlockedType(EntityType<?> type) {
        if (type == EntityType.WARDEN) {
            return Config.enableWardenUnlock();
        }
        if (type == EntityType.ELDER_GUARDIAN) {
            return Config.enableElderGuardianUnlock();
        }
        // Everything else - including the #c:bosses tag members - keeps Apotheosis' refusal.
        return false;
    }

    /**
     * Strips the Apotheosis "banned" lines from a tooltip of an egg we have unlocked, leaving every unrelated line
     * intact.
     * <p>
     * The tooltip line is removed only when the mob's toggle is on, so an egg that is still refused keeps showing the
     * hint.
     *
     * @param stack   the stack being rendered
     * @param tooltip the mutable tooltip line list
     */
    static void removeBannedLine(ItemStack stack, List<Component> tooltip) {
        if (stack.isEmpty() || tooltip.isEmpty() || !isUnlockedEgg(stack)) {
            return;
        }
        tooltip.removeIf(component -> {
            if (!(component.getContents() instanceof TranslatableContents translatable)) {
                return false;
            }
            String key = translatable.getKey();
            for (String banned : BANNED_KEYS) {
                if (banned.equals(key)) {
                    return true;
                }
            }
            return false;
        });
    }
}
