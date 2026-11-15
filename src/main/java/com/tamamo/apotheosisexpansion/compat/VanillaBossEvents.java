package com.tamamo.apotheosisexpansion.compat;

import com.mojang.logging.LogUtils;

import com.tamamo.apotheosisexpansion.Config;

import org.slf4j.Logger;

import dev.shadowsoffire.apotheosis.tiers.GenContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Game-bus listeners that convert vanilla bosses into Apotheosis bosses.
 * <p>
 * Three different hook points are needed because the three mobs enter the world in three different ways - see
 * {@link VanillaBossHooks} for why none of them goes through Apotheosis' own invader path.
 */
public final class VanillaBossEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ---------------------------------------------------------------- warden

    /**
     * The warden is spawned by {@code SculkShriekerBlockEntity} with {@code MobSpawnType.TRIGGERED}, which never
     * reaches Apotheosis' invader gate.
     * <p>
     * Hooked at {@code FinalizeSpawnEvent} rather than on join, because that event fires with the level and position
     * already resolved and gives us the spawning player context for the world tier.
     */
    @SubscribeEvent
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        // AllTheModium Piglich: 1.21.1 dropped the random ATM armour that 1.20.x had, so it is restored
        // here before Apotheosis turns the equipment into affixed loot.
        if (event.getEntity() instanceof Mob mob && PiglichGearHook.isPiglich(mob)) {
            if (!event.getLevel().getLevel().isClientSide()) {
                if (PiglichGearHook.tryEquipAndConvert(mob, event.getLevel().getLevel().getRandom())) {
                    LOGGER.info("[ApotheosisExpansion] Converted an AllTheModium Piglich into a boss.");
                }
            }
            return;
        }

        if (!(event.getEntity() instanceof Warden warden)) {
            return;
        }
        if (event.getSpawnType() != net.minecraft.world.entity.MobSpawnType.TRIGGERED) {
            return;
        }
        if (event.getLevel().getLevel().isClientSide()) {
            return;
        }

        Player player = event.getLevel().getLevel().getNearestPlayer(
            event.getX(), event.getY(), event.getZ(), -1, false);
        if (player == null) {
            return;
        }

        GenContext ctx = GenContext.forPlayerAtPos(
            event.getLevel().getLevel().getRandom(), player, warden.blockPosition());

        if (VanillaBossHooks.tryConvertWarden(warden, ctx)) {
            LOGGER.info("[ApotheosisExpansion] Converted a summoned warden into a boss.");
        }
    }

    // ---------------------------------------------------------------- wither + dragon

    /**
     * The wither and the ender dragon are both created and added directly, never calling {@code finalizeSpawn}, so
     * {@code EntityJoinLevelEvent} is the only reliable hook.
     * <p>
     * {@code loadedFromDisk()} is checked because a chunk reload would otherwise re-roll the wither every time the
     * area is loaded, and could re-convert a dragon that was already handled.
     */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (event.loadedFromDisk()) {
            return;
        }

        Entity entity = event.getEntity();

        // EntityJoinLevelEvent also fires on teleports and re-adds, not just on first spawn.
        // Anything already tagged as an Apotheosis boss must not be converted again, or the stat
        // modifiers would stack and the affixes would be re-rolled.
        if (apotheosisExpansion$alreadyConverted(entity)) {
            return;
        }

        if (entity instanceof WitherBoss wither) {
            // The wither starts invulnerable in its spawn animation; converting now is safe (only stats/gear change)
            // and avoids a window where the player could already have hit it as a normal wither.
            if (VanillaBossHooks.tryConvertWither(wither, serverLevel.getRandom())) {
                LOGGER.info("[ApotheosisExpansion] Converted a player-built wither into a boss at {}.", wither.blockPosition());
            }
            return;
        }

        if (entity instanceof EnderDragon dragon) {
            handleDragon(serverLevel, dragon);
        }
    }

    /**
     * Converts the dragon only when this saved world has never had one killed.
     * <p>
     * The flag used is {@link EndDragonFight#hasPreviouslyKilledDragon()}, the same one vanilla consults before
     * granting first-kill rewards. It is only <i>read</i> here, never written, so no other mod's dragon-egg logic is
     * disturbed.
     */
    private void handleDragon(ServerLevel level, EnderDragon dragon) {
        EndDragonFight fight = level.getDragonFight();
        if (fight == null) {
            return;
        }
        if (fight.hasPreviouslyKilledDragon()) {
            // Not the first dragon in this world - leave it vanilla.
            return;
        }

        if (VanillaBossHooks.tryConvertFirstDragon(dragon, level.getRandom())) {
            LOGGER.info("[ApotheosisExpansion] Converted the first ender dragon of this world into a boss.");
        }
    }

    /** True when this entity was already turned into an Apotheosis boss by this mod. */
    private static boolean apotheosisExpansion$alreadyConverted(Entity entity) {
        return entity.getPersistentData().getBoolean(dev.shadowsoffire.apotheosis.mobs.types.Invader.BOSS_KEY);
    }

    /**
     * Diagnostics: on server start, report which boss entries actually loaded.
     * <p>
     * Useful because a data pack that has not been reloaded yet leaves the entries absent, and a silent no-op is hard
     * to tell apart from "the feature is off".
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        boolean group = Config.enableVanillaBosses();
        LOGGER.info("[ApotheosisExpansion] Vanilla boss conversion: group={}, warden={}, wither={}, dragon={}",
            group,
            Config.isEntityInvaderEnabled("minecraft:warden"),
            Config.isEntityInvaderEnabled("minecraft:wither"),
            Config.isEntityInvaderEnabled("minecraft:ender_dragon"));
    }

}
