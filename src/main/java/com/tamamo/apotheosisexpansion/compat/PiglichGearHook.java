package com.tamamo.apotheosisexpansion.compat;

import java.util.Optional;

import com.mojang.logging.LogUtils;

import com.tamamo.apotheosisexpansion.Config;

import org.slf4j.Logger;

import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.mobs.registries.InvaderRegistry;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Gives the AllTheModium Piglich its random ATM armour, then lets Apotheosis roll affixes on it.
 * <p>
 * <b>Why this is needed.</b> The 1.20.x version of AllTheModium equipped the Piglich with a 50% chance per
 * armour piece ({@code PiglichEntity.populateDefaultEquipementSlots} + {@code maybeWearArmor}). The 1.21.1
 * rewrite dropped that: {@code finalizeSpawn} is now an empty override that just returns its argument, so
 * the mob arrives naked. Apotheosis' gear-set system cannot reproduce "maybe wear nothing" either - its
 * {@code GearSet.apply} performs one weighted draw per slot and always equips the winner - so the randomness
 * has to be reintroduced here.
 * <p>
 * <b>Ordering matters.</b> The armour is applied <i>before</i> {@code Invader#initBoss} runs, because
 * {@code initBoss} is what turns existing equipment into affixed loot. Applying armour afterwards would
 * leave it plain.
 * <p>
 * Only the AllTheModium tier is used - not Vibranium or Unobtainium - per the agreed spec.
 */
public final class PiglichGearHook {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation PIGLICH_ENTITY = ResourceLocation.tryParse("allthemodium:piglich");

    private static final ResourceLocation PIGLICH_INVADER =
        ResourceLocation.tryParse("apotheosis:apothic_invaders/allthemodium/piglich");

    /** One roll per piece, matching the 1.20.x behaviour. */
    private static final float ARMOR_CHANCE = 0.5F;

    private static final String ATM_SWORD_ID = "allthemodium:allthemodium_sword";

    /** 1.20.x gave a 40% chance of the ATM sword, otherwise netherite. */
    private static final float ATM_SWORD_CHANCE = 0.4F;

    /** slot -> AllTheModium item id. Order is fixed so the RNG sequence is deterministic per spawn. */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] ARMOR_IDS = {
        "allthemodium:allthemodium_helmet",
        "allthemodium:allthemodium_chestplate",
        "allthemodium:allthemodium_leggings",
        "allthemodium:allthemodium_boots",
    };

    private PiglichGearHook() {}

    /**
     * Equips the piglich and converts it into an Apotheosis boss.
     *
     * @return true when the conversion happened
     */
    public static boolean tryEquipAndConvert(Mob piglich, RandomSource random) {
        if (!Config.enableAllthemodiumInvaders() || !Config.isEntityInvaderEnabled("allthemodium:piglich")) {
            return false;
        }

        equipRandomArmor(piglich, random);

        if (!(piglich.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return false;
        }

        Optional<Invader> entry = InvaderLookup.find(PIGLICH_INVADER);
        if (entry.isEmpty()) {
            // Armour is already on; leaving it plain is still better than reverting it.
            LOGGER.debug("[ApotheosisExpansion] Piglich invader entry not loaded; armour applied without affixes.");
            return false;
        }

        // The pyramid lives in allthemodium:the_other and is a late-game structure, so pin the tier high
        // rather than deriving it from a player who may be nowhere near.
        GenContext ctx = GenContext.standalone(random, WorldTier.PINNACLE, 0F, serverLevel, piglich.blockPosition());

        LootRarity rarity = InvaderLookup.bestRarity(entry.get());
        if (rarity == null) {
            return false;
        }

        entry.get().initBoss(piglich, ctx, rarity);
        piglich.getPersistentData().putBoolean(Invader.BOSS_KEY, true);
        ResourceLocation rarityId = RarityRegistry.INSTANCE.getKey(rarity);
        if (rarityId != null) {
            piglich.getPersistentData().putString(Invader.RARITY_KEY, rarityId.toString());
        }
        LOGGER.info("[ApotheosisExpansion] Converted a Piglich into a boss with rarity {}.", rarityId);
        return true;
    }

    /**
     * Independent 50% roll per armour slot, plus the sword roll. Missing items are skipped rather than
     * failing, so a mismatched AllTheModium version degrades to a partially equipped piglich.
     */
    private static void equipRandomArmor(Mob piglich, RandomSource random) {
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            if (random.nextFloat() >= ARMOR_CHANCE) {
                continue; // this slot stays empty, exactly like the 1.20.x maybeWearArmor
            }
            Item item = resolveItem(ARMOR_IDS[i]);
            if (item == null) {
                LOGGER.debug("[ApotheosisExpansion] AllTheModium armour piece {} is missing; skipping slot {}.", ARMOR_IDS[i], ARMOR_SLOTS[i]);
                continue;
            }
            piglich.setItemSlot(ARMOR_SLOTS[i], new ItemStack(item));
        }

        // Main hand: 40% ATM sword, otherwise netherite sword (1.20.x parity).
        // The roll is taken first and only then resolved, so a missing ATM sword degrades to the
        // netherite branch instead of silently shifting the probability.
        Item mainhand;
        if (random.nextFloat() < ATM_SWORD_CHANCE) {
            mainhand = resolveItem(ATM_SWORD_ID);
            if (mainhand == null) {
                LOGGER.debug("[ApotheosisExpansion] {} is missing; falling back to netherite.", ATM_SWORD_ID);
                mainhand = Items.NETHERITE_SWORD;
            }
        }
        else {
            mainhand = Items.NETHERITE_SWORD;
        }
        piglich.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(mainhand));
    }

    /**
     * Looks an item up by id.
     *
     * @return the item, or null when the id is malformed or absent from the registry
     */
    private static Item resolveItem(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? null : item;
    }

    /** True when the mob is the AllTheModium Piglich. Compares registry keys, not class identity. */
    public static boolean isPiglich(Mob mob) {
        return PIGLICH_ENTITY.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
    }

}
