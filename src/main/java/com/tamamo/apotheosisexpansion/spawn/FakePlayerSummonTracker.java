package com.tamamo.apotheosisexpansion.spawn;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Remembers which player issued a {@code /player <name> spawn} command, so the fake player that command creates can
 * take that player's world tier.
 *
 * <h2>Why the summoner cannot be read where the fake is created</h2>
 * carpet's {@code PlayerCommand.spawn(CommandContext)} is the only place that still knows the {@code CommandSourceStack},
 * i.e. who ran the command. It then calls {@code EntityPlayerMPFake.createFake(name, server, pos, ...)}, whose signature
 * carries no source, and that method finishes the work inside a {@code CompletableFuture.thenAcceptAsync} callback -
 * another thread, later in time. By the time the new player reaches {@code PlayerList.placeNewPlayer}, the caller is
 * nowhere in scope and not on the stack.
 *
 * <p>So the identity is captured <em>at command time</em> and parked here, keyed by the fake's name and the server
 * instance. {@link FakePlayerTierSync} claims it when that fake joins. The handoff is intentionally narrow:
 * <ul>
 * <li>One entry per (server, lower-cased name), so two players spawning the same name in the same tick cannot be
 * confused with each other beyond the last one winning - which matches carpet, where the second spawn fails anyway.</li>
 * <li>An entry expires after {@link #TTL_TICKS}, so a command that ends up creating nothing (name too long, position
 * outside the world, profile fetch failing) cannot leak a stale owner onto a completely unrelated fake spawned minutes
 * later.</li>
 * <li>The command source may be a command block: {@code CommandSourceStack.getEntity()} is consulted when there is no
 * player, and a block that was placed/activated by someone still gets its own position used for the fallback tier
 * lookup. A source with neither a player nor an entity simply records nothing.</li>
 * </ul>
 */
public final class FakePlayerSummonTracker {

    private FakePlayerSummonTracker() {}

    /**
     * How long a pending summon stays claimable.
     * <p>
     * Carpet creates the fake within the same tick when the profile is already cached, but the first spawn of a
     * never-seen name does a profile lookup off-thread. 40 ticks covers that comfortably without keeping an abandoned
     * entry around long enough to attach to an unrelated fake.
     */
    private static final long TTL_TICKS = 40L;

    /** One remembered summon: who asked, and the tick after which the record is stale. */
    private record Pending(int serverId, String name, UUID summonerId, String summonerName, long expiresAt) {}

    /** Small, and only ever touched from the server thread: a plain map is enough (one entry per name per server). */
    private static final java.util.Map<String, Pending> PENDING = new java.util.HashMap<>();

    /**
     * Records that {@code summoner} is about to spawn a fake named {@code fakeName}.
     *
     * @param summoner the player that ran the command, or {@code null} when it came from a non-player source
     */
    public static void record(MinecraftServer server, String fakeName, @Nullable ServerPlayer summoner, @Nullable Entity sourceEntity) {
        if (server == null || fakeName == null || fakeName.isBlank()) {
            return;
        }
        // Prefer a real player; fall back to whatever entity ran the command (command block cart, etc).
        UUID id = null;
        String name = null;
        if (summoner != null) {
            id = summoner.getUUID();
            name = summoner.getGameProfile().getName();
        }
        else if (sourceEntity != null && !(sourceEntity instanceof Player)) {
            // A non-player source still gets a stable identity so the join can tell "this came from a block".
            id = sourceEntity.getUUID();
            name = sourceEntity.getName().getString();
        }
        if (id == null) {
            return;
        }

        long now = server.getTickCount();
        PENDING.put(key(server, fakeName), new Pending(System.identityHashCode(server), fakeName.toLowerCase(), id, name, now + TTL_TICKS));
    }

    /**
     * Claims the pending owner for a fake that just joined, removing the record.
     *
     * @return the summoner's UUID, or {@code null} when nothing is pending or the record already expired
     */
    @Nullable
    public static UUID claim(MinecraftServer server, String fakeName) {
        if (server == null || fakeName == null) {
            return null;
        }
        String key = key(server, fakeName);
        Pending pending = PENDING.remove(key);
        if (pending == null) {
            return null;
        }
        if (server.getTickCount() > pending.expiresAt()) {
            return null;
        }
        return pending.summonerId();
    }

    /** Name of the player that claimed the last summon, for logging. */
    @Nullable
    public static String claimName(MinecraftServer server, String fakeName) {
        PENDING.remove(key(server, fakeName));
        return null;
    }

    /** Drops everything. Called when a world unloads so a shutdown does not leave records behind. */
    public static void clear() {
        PENDING.clear();
    }

    /** Drops records older than the TTL; cheap and called opportunistically from {@link #record}. */
    private static void prune(int serverId, long now) {
        PENDING.values().removeIf(p -> p.serverId() != serverId || now > p.expiresAt());
    }

    private static String key(MinecraftServer server, String fakeName) {
        return System.identityHashCode(server) + "|" + fakeName.toLowerCase();
    }
}
