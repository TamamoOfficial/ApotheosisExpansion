package com.tamamo.apotheosisexpansion.compat;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.tamamo.apotheosisexpansion.Config;

import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.mobs.registries.InvaderRegistry;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Applies Apotheosis boss data to vanilla boss mobs that Apotheosis' own spawn hooks never see.
 * <p>
 * <b>Why hooks instead of data files.</b> Apotheosis selects invaders inside
 * {@code FinalizeSpawnEvent} and only for {@code NATURAL}/{@code CHUNK_GENERATION} spawns. None of the three
 * vanilla bosses qualifies:
 * <ul>
 * <li><b>Warden</b> is spawned by {@code SculkShriekerBlockEntity} with {@code MobSpawnType.TRIGGERED}.</li>
 * <li><b>Wither</b> is built by {@code WitherSkullBlock} and added with {@code addFreshEntity}, never calling
 * {@code finalizeSpawn}.</li>
 * <li><b>Ender Dragon</b> is created by {@code EndDragonFight.createNewDragon} and also added directly.</li>
 * </ul>
 * The data files under {@code apothic_invaders/vanilla_bosses/} therefore never take part in a
 * random draw; they are the stat carrier this class looks up by entity id and feeds to
 * {@link Invader#initBoss(Mob, GenContext, LootRarity)}.
 */
public final class VanillaBossHooks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Registry ids of the entries this class consumes. Namespace is apotheosis (they live in its registry). */
    private static final ResourceLocation WARDEN_ID = id("apotheosis:apothic_invaders/vanilla_bosses/warden");
    private static final ResourceLocation WITHER_ID = id("apotheosis:apothic_invaders/vanilla_bosses/wither");
    private static final ResourceLocation DRAGON_ID = id("apotheosis:apothic_invaders/vanilla_bosses/ender_dragon");
    private static final ResourceLocation DRAGON_ANCIENT_ID = id("apotheosis:apothic_invaders/vanilla_bosses/ender_dragon_ancient");

    /** The rarity AncientReforging registers. Absent unless that mod is installed. */
    private static final ResourceLocation ANCIENT_RARITY = id("ancientreforging:ancient");

    private static final int WITHER_CHANCE = 10;   // roll out of 100
    private static final int WARDEN_CHANCE = 10;   // roll out of 100

    private VanillaBossHooks() {}

    private static ResourceLocation id(String s) {
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl == null) {
            throw new IllegalStateException("Bad resource location: " + s);
        }
        return rl;
    }

    // ------------------------------------------------------------------ wither

    /**
     * Rolls the 10% chance for a player-built wither to become a boss.
     *
     * @param wither the freshly created wither
     * @param random world random source
     * @return true when it was converted (caller may then skip further handling)
     */
    public static boolean tryConvertWither(Mob wither, net.minecraft.util.RandomSource random) {
        if (!Config.enableVanillaBosses() || !Config.isEntityInvaderEnabled("minecraft:wither")) {
            return false;
        }
        if (random.nextInt(100) >= WITHER_CHANCE) {
            return false;
        }

        Level level = wither.level();
        ResourceLocation dim = level.dimension().location();

        // Tiers by dimension, per the spec: overworld = ascent..summit, nether = ascent..pinnacle,
        // anywhere else the wither stays vanilla.
        Set<WorldTier> allowed;
        if ("minecraft:overworld".equals(dim.toString())) {
            allowed = Set.of(WorldTier.ASCENT, WorldTier.SUMMIT);
        }
        else if ("minecraft:the_nether".equals(dim.toString())) {
            allowed = Set.of(WorldTier.ASCENT, WorldTier.SUMMIT, WorldTier.PINNACLE);
        }
        else {
            return false;
        }

        GenContext ctx = GenContext.standalone(random, pickTier(random, allowed), 0F,
            (net.minecraft.server.level.ServerLevel) level, wither.blockPosition());
        return convert(wither, WITHER_ID, ctx, allowed);
    }

    // ------------------------------------------------------------------ warden

    /**
     * Rolls the 10% chance for a shrieker-summoned warden to become a boss.
     * <p>
     * Called once per successful summon, so the check repeats every time a shrieker triggers.
     */
    public static boolean tryConvertWarden(Mob warden, GenContext baseCtx) {
        if (!Config.enableVanillaBosses() || !Config.isEntityInvaderEnabled("minecraft:warden")) {
            return false;
        }
        net.minecraft.util.RandomSource random = baseCtx.rand();
        if (random.nextInt(100) >= WARDEN_CHANCE) {
            return false;
        }
        // ascent..pinnacle, i.e. everything except the two earliest tiers
        Set<WorldTier> allowed = Set.of(WorldTier.ASCENT, WorldTier.SUMMIT, WorldTier.PINNACLE);
        return convert(warden, WARDEN_ID, baseCtx, allowed);
    }

    // ------------------------------------------------------------------ ender dragon

    /**
     * Converts the world's first ender dragon into a boss.
     * <p>
     * Rarity selection: when AncientReforging's rarity exists, the ancient variant is used, otherwise mythic.
     * This is a runtime lookup rather than a data condition because the rarity and the invader entry live in the
     * same registry pass - a data-file condition could not express "use entry B when rarity X exists".
     */
    public static boolean tryConvertFirstDragon(Mob dragon, net.minecraft.util.RandomSource random) {
        if (!Config.enableVanillaBosses() || !Config.isEntityInvaderEnabled("minecraft:ender_dragon")) {
            return false;
        }
        if (!(dragon.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return false;
        }

        GenContext ctx = GenContext.standalone(random, WorldTier.PINNACLE, 0F, serverLevel, dragon.blockPosition());

        boolean ancientAvailable = RarityRegistry.INSTANCE.getValue(ANCIENT_RARITY) != null;
        if (ancientAvailable) {
            Optional<Invader> ancientEntry = InvaderLookup.find(DRAGON_ANCIENT_ID);
            LootRarity ancient = RarityRegistry.INSTANCE.getValue(ANCIENT_RARITY);
            if (ancientEntry.isPresent() && ancient != null) {
                ancientEntry.get().initBoss(dragon, ctx, ancient);
                markBoss(dragon, ANCIENT_RARITY);
                LOGGER.info("[ApotheosisExpansion] First ender dragon converted with ancient rarity.");
                return true;
            }
        }

        Optional<Invader> fallback = InvaderLookup.find(DRAGON_ID);
        LootRarity mythic = RarityRegistry.INSTANCE.getValue(id("apotheosis:mythic"));
        if (fallback.isPresent() && mythic != null) {
            fallback.get().initBoss(dragon, ctx, mythic);
            markBoss(dragon, id("apotheosis:mythic"));
            LOGGER.info("[ApotheosisExpansion] First ender dragon converted with mythic rarity.");
            return true;
        }

        LOGGER.warn("[ApotheosisExpansion] Could not convert the first ender dragon: invader entry or rarity missing.");
        return false;
    }

    // ------------------------------------------------------------------ shared

    private static boolean convert(Mob mob, ResourceLocation invaderId, GenContext ctx, Set<WorldTier> allowedTiers) {
        Optional<Invader> entry = InvaderLookup.find(invaderId);
        if (entry.isEmpty()) {
            return false;
        }
        Invader invader = entry.get();

        // Only pick rarities this entry actually carries stats for; that is what initBoss requires.
        LootRarity rarity = InvaderLookup.bestRarity(invader);
        if (rarity == null) {
            return false;
        }

        invader.initBoss(mob, ctx, rarity);
        markBoss(mob, RarityRegistry.INSTANCE.getKey(rarity));
        return true;
    }



    private static WorldTier pickTier(net.minecraft.util.RandomSource random, Set<WorldTier> allowed) {
        WorldTier[] options = allowed.toArray(new WorldTier[0]);
        return options[random.nextInt(options.length)];
    }

    /** Records the conversion in persistent data so other systems (and later debugging) can see it. */
    private static void markBoss(Mob mob, ResourceLocation rarityId) {
        mob.getPersistentData().putBoolean(Invader.BOSS_KEY, true);
        if (rarityId != null) {
            mob.getPersistentData().putString(Invader.RARITY_KEY, rarityId.toString());
        }
    }

}
