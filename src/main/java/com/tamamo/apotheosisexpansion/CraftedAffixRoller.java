package com.tamamo.apotheosisexpansion;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.shadowsoffire.apotheosis.Apoth.Components;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.LootRule;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Applies Apotheosis affixes and gem sockets to an item a player just crafted.
 * <p>
 * The two features are toggled independently but share a single rarity roll, so an item can never end up with
 * unrelated rarities for its affixes and its sockets. The rarity is only rolled when at least one feature is active.
 * <p>
 * Two code paths exist on purpose:
 * <ul>
 * <li><b>Affixes enabled</b> — {@link LootController#createLootItem} is used, which is exactly what Apotheosis itself
 * uses for loot. It writes the rarity, runs every rule from the rarity's data file (affixes, sockets, durability,
 * unbreakable, ...) and applies the coloured affix name. The socket component is stripped afterwards if the socket
 * feature is disabled, so a disabled toggle genuinely does nothing.</li>
 * <li><b>Affixes disabled, sockets enabled</b> — the socket counts are taken from the rarity's own
 * {@code apotheosis:socket} rules and written directly, without touching affixes, rarity colour or item name.</li>
 * </ul>
 */
public final class CraftedAffixRoller {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CraftedAffixRoller() {}

    /**
     * Decides whether the stack is a candidate at all. Kept separate from {@link #apply} so the Mixin can bail out
     * cheaply for the vast majority of crafting operations (planks, torches, food, ...).
     *
     * @param stack  the freshly crafted stack, on the server thread
     * @param player the crafting player
     * @return true when generation should be attempted
     */
    public static boolean isCandidate(ItemStack stack, Player player) {
        if (stack.isEmpty() || player == null) {
            return false;
        }
        if (!Config.enableAffixes() && !Config.enableSockets()) {
            return false;
        }

        // Must be an item Apotheosis knows how to decorate. Non-equipment resolves to LootCategory.NONE, and
        // createLootItem would still write a rarity component onto it, so this guard is load-bearing.
        if (!Config.allowNonEquipment()) {
            LootCategory cat = LootCategory.forItem(stack);
            if (cat == null || cat.isNone()) {
                return false;
            }
        }

        if (Config.skipIfAlreadyAffixed() && hasExistingApothData(stack)) {
            return false;
        }
        return true;
    }

    /** True when the item already carries affixes, a rarity, or sockets. */
    public static boolean hasExistingApothData(ItemStack stack) {
        if (stack.has(Components.AFFIXES) || stack.has(Components.RARITY)) {
            return true;
        }
        // SOCKETS is an Integer component; compare against 0 rather than presence so an explicit 0 is not treated
        // as "already socketed".
        return stack.getOrDefault(Components.SOCKETS, 0) > 0;
    }

    /**
     * Rolls and applies affixes and/or sockets to the stack, modifying it in place.
     *
     * @param stack  the crafted stack (already validated by {@link #isCandidate})
     * @param player the crafting player
     * @return true when the stack was modified
     */
    public static boolean apply(ItemStack stack, Player player) {
        RandomSource rand = player.getRandom();

        boolean wantAffixes = Config.enableAffixes() && rand.nextDouble() < Config.affixChance();
        boolean wantSockets = Config.enableSockets() && rand.nextDouble() < Config.socketChance();

        if (!wantAffixes && !wantSockets) {
            return false;
        }

        // One shared rarity roll for both features.
        GenContext ctx = GenContext.forPlayer(rand, player);
        LootRarity rarity = RaritySelector.select(ctx);
        if (rarity == null) {
            LOGGER.debug("[ApotheosisExpansion] No rarity available; skipping generation for {}.", stack.getItem());
            return false;
        }

        boolean changed;
        if (wantAffixes) {
            changed = applyAffixes(stack, rarity, ctx, wantSockets);
        }
        else {
            changed = applySocketsOnly(stack, rarity, ctx);
        }
        return changed;
    }

    /**
     * Full loot generation identical to Apotheosis' own, with the socket rules neutralised when sockets are disabled.
     */
    private static boolean applyAffixes(ItemStack stack, LootRarity rarity, GenContext ctx, boolean keepSockets) {
        LootCategory cat = LootCategory.forItem(stack);
        if (cat == null || cat.isNone()) {
            return false;
        }

        // Remember what the item already had. createLootItem does not touch SOCKETS, so any socket value after the
        // call that differs from this baseline was granted by the rarity's rules during this roll.
        int preexistingSockets = stack.getOrDefault(Components.SOCKETS, 0);

        ItemStack before = stack.copy();
        LootController.createLootItem(stack, cat, rarity, ctx);

        if (!keepSockets) {
            // The rarity's rules may have granted sockets; a disabled toggle must not leak through.
            // Only strip sockets this roll introduced - never ones the item legitimately carried beforehand.
            if (preexistingSockets <= 0) {
                stack.remove(Components.SOCKETS);
                stack.remove(Components.SOCKETED_GEMS);
            }
        }
        else if (stack.getOrDefault(Components.SOCKETS, 0) <= 0) {
            // Rarity had no socket rule (e.g. common): fall back to the configured range so "sockets on" is visible.
            applySocketRange(stack, ctx);
        }

        return !ItemStack.matches(before, stack);
    }

    /**
     * Sockets only: reuse the rarity's own socket rules, without writing affixes or a rarity colour.
     */
    private static boolean applySocketsOnly(ItemStack stack, LootRarity rarity, GenContext ctx) {
        // An item with no loot category must not receive sockets either. The early return in #applyAffixes already
        // covers the affix path, but this one used to fall through to the socket-range fallback below, so a
        // 'none' item (a block, or Mekanism's energy gear pinned to apotheosis:none) would silently gain sockets
        // whenever allow_non_equipment was on. Both paths now agree that 'none' means untouched.
        LootCategory cat = LootCategory.forItem(stack);
        if (cat == null || cat.isNone()) {
            return false;
        }

        // The rules write SOCKETS themselves; socketsFromRules returns the resulting absolute count.
        int granted = socketsFromRules(rarity, stack, ctx);
        if (granted <= 0) {
            // Rarity defines no socket rule (e.g. common): use the configured fallback range.
            granted = rollSocketRange(ctx);
        }
        if (granted <= 0) {
            return false;
        }
        SocketHelper.setSockets(stack, granted);
        return true;
    }

    /**
     * Executes only the socket rules belonging to the rarity, ignoring every other rule type.
     * <p>
     * The rules are read through {@link LootRarity#getRules(LootCategory)} so rarity overrides (which can redefine the
     * rule list per loot category) are respected.
     * <p>
     * {@code SocketLootRule} never lowers the socket count ("will not reduce the number of sockets already on the
     * item"), so the post-execution value is the authoritative one; it is returned as an absolute count rather than a
     * delta so callers cannot accidentally treat it as something to add.
     *
     * @return the resulting socket count, or 0 when the rarity defines no socket rule at all
     */
    private static int socketsFromRules(LootRarity rarity, ItemStack stack, GenContext ctx) {
        LootCategory cat = LootCategory.forItem(stack);
        if (cat == null || cat.isNone()) {
            return 0;
        }

        boolean sawSocketRule = false;
        for (LootRule rule : flatten(rarity.getRules(cat))) {
            if (rule instanceof LootRule.SocketLootRule) {
                sawSocketRule = true;
                rule.execute(stack, rarity, ctx);
            }
        }
        return sawSocketRule ? SocketHelper.getSockets(stack) : 0;
    }

    /**
     * Expands {@code combined} wrappers so nested socket rules are found. {@code chanced} / {@code select} wrappers are
     * deliberately left intact: unwrapping them would silently bypass their probability, which would make sockets far
     * more common than the Apotheosis data files intend.
     */
    private static List<LootRule> flatten(List<LootRule> rules) {
        List<LootRule> out = new ArrayList<>(rules.size());
        for (LootRule rule : rules) {
            if (rule instanceof LootRule.CombinedLootRule combined) {
                out.addAll(flatten(combined.rules()));
            }
            else {
                out.add(rule);
            }
        }
        return out;
    }

    /** Writes a socket count drawn from the configured fallback range. */
    private static void applySocketRange(ItemStack stack, GenContext ctx) {
        int sockets = rollSocketRange(ctx);
        if (sockets > 0) {
            SocketHelper.setSockets(stack, sockets);
        }
    }

    private static int rollSocketRange(GenContext ctx) {
        int min = Math.min(Config.socketMin(), Config.socketMax());
        int max = Math.max(Config.socketMin(), Config.socketMax());
        if (max <= 0) {
            return 0;
        }
        return min + ctx.rand().nextInt(max - min + 1);
    }

    /**
     * Convenience wrapper used by the Mixin: validates and applies in one call.
     *
     * @return true when the stack was modified
     */
    public static boolean tryApply(ItemStack stack, Player player) {
        if (!isCandidate(stack, player)) {
            return false;
        }
        return apply(stack, player);
    }
}
