package com.tamamo.apotheosisexpansion;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/**
 * Makes a player come back from death with the full health their gear grants, instead of a flat 20 HP.
 *
 * <p><b>The bug.</b> On MC 1.21.1 a respawn builds a brand-new {@link ServerPlayer} and copies state into it in
 * {@code ServerPlayer#restoreFrom}:
 * <pre>
 *   this.getAttributes().assignBaseValues(that.getAttributes());  // base values only
 *   this.setHealth(this.getMaxHealth());                          // <- reads max health HERE
 *   ... this.getInventory().replaceWith(that.getInventory());      // gear arrives AFTER the health was read
 *   ... EventHooks.onPlayerClone(...)                              // fires last
 * </pre>
 * {@code assignBaseValues} copies only {@code baseValue}; item-contributed modifiers are deliberately dropped and
 * re-applied per slot by {@code LivingEntity#detectEquipmentUpdates} on the next tick. The fresh player therefore
 * still has base max health (20) when {@code setHealth(getMaxHealth())} executes, so it starts at 20 HP regardless
 * of what it is wearing. Apotheosis' {@code +max health} affixes (e.g. the {@code blessed} armor affix) are exactly
 * such modifiers, so a player wearing +40 max health still respawns at 20/60.
 *
 * <p>Vanilla never hit this because vanilla max health is the constant 20. Apotheosis' own attempt to keep health
 * percent stable across equipment changes ({@code apoth_updateHealthPct} on {@code detectEquipmentUpdates}) is
 * explicitly skipped for players ({@code if (!this.isPlayer())}), and it restores a <i>percentage</i> anyway, which
 * is not what a respawn wants.
 *
 * <p><b>The fix.</b> Let Apotheosis do its thing — the modifiers are correct one tick after the respawn — and then
 * re-apply vanilla's own respawn rule, {@code setHealth(getMaxHealth())}, once the real max health is known. The
 * result is exactly vanilla semantics ("a respawned player is at full health"), just measured against the player's
 * <i>actual</i> maximum instead of a hard-coded 20.
 *
 * <p><b>Why the tick after, and not the Clone event.</b> {@code PlayerEvent.Clone} fires inside {@code restoreFrom},
 * before the new player has ticked, so at that point {@code getMaxHealth()} still excludes the item modifiers.
 * {@link PlayerTickEvent.Post} runs at the tail of {@code Player#tick}, after {@code LivingEntity#tick} has called
 * {@code detectEquipmentUpdates} and {@code refreshDirtyAttributes}, so {@code getMaxHealth()} is the true post-gear
 * value and the attribute map is no longer dirty.
 *
 * <p>{@code PlayerEvent.Clone} fires for dimension changes too (End return, portals), where health is supposed to be
 * carried over rather than reset. Those are excluded via {@code isWasDeath()}.
 *
 * <p><b>Why not record the health at the moment of death.</b> By the time any death event fires,
 * {@code actuallyHurt} has already run {@code setHealth(getHealth() - damage)}, which clamps to 0 — any recorded
 * ratio would be 0. Vanilla does not preserve a death ratio either: {@code restoreFrom} unconditionally sets full
 * health. Reusing that rule keeps behaviour predictable and requires no extra tracking.
 *
 * <p>Pending entries are keyed by UUID and removed on consumption, so a stale entry cannot survive a respawn, and
 * they are dropped on logout so a player quitting right after dying cannot leak one.
 */
public final class RespawnHealthHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Players that died and are waiting for their first post-respawn tick. */
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    public RespawnHealthHandler() {}

    /**
     * Marks a player whose respawn came from death.
     * <p>
     * Fires inside {@code ServerPlayer#restoreFrom}, i.e. before the new player's first tick, which is exactly when
     * we need to start listening but cannot yet read a correct max health.
     */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Dimension changes (End return, portals) must keep vanilla carry-over behaviour.
        if (!event.isWasDeath()) {
            return;
        }
        if (!Config.enableRespawnHealthFix()) {
            return;
        }
        PENDING.add(player.getUUID());
        LOGGER.debug("[ApotheosisExpansion] Player {} died; will re-apply full health on the next tick.",
            player.getGameProfile().getName());
    }

    /**
     * Applies vanilla's {@code setHealth(getMaxHealth())} once the real max health is available.
     * <p>
     * Registered on {@code Post} so {@code detectEquipmentUpdates} has already re-applied the item attribute
     * modifiers (and {@code refreshDirtyAttributes} has recalculated max health).
     */
    @SubscribeEvent
    public void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide) {
            return;
        }
        if (!PENDING.remove(player.getUUID())) {
            return;
        }
        // Never resurrect: only a live player gets topped up, and only if still below their maximum.
        if (!player.isAlive()) {
            return;
        }
        float max = player.getMaxHealth();
        if (max <= 0.0F || player.getHealth() >= max) {
            return;
        }
        player.setHealth(max);
        LOGGER.debug("[ApotheosisExpansion] Respawned {} at full health {}/{}.",
            player.getGameProfile().getName(), player.getHealth(), max);
    }

    /** Drops a pending entry when the player leaves, so nothing outlives the session. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }
}
