package com.tamamo.apotheosisexpansion.spawn;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Copies the summoner's world tier onto carpet-style fake players.
 *
 * <h2>The problem</h2>
 * Apotheosis stores the world tier in a player attachment that only {@code WorldTier.setTier} writes. A fake player is
 * a real {@code ServerPlayer} that never goes through the login/select-tier path, so its attachment keeps the default
 * {@code HAVEN}. {@link MobCapScaler} groups nearby players by tier, so a fake player next to a pinnacle summoner counts
 * as a separate one-player group and the bonus never triggers - a two-player test on one platform shows the vanilla 70.
 *
 * <h2>How the tier is resolved</h2>
 * Three sources, in priority order:
 * <ol>
 * <li><b>Same name as an online player</b> - the fake is "the same player", so it adopts that player's tier. This is
 * what makes {@code /player Steve spawn} behave like Steve's own body, including when a different player re-spawns the
 * fake later: whoever the name currently belongs to wins.</li>
 * <li><b>Same UUID as an online player</b> - covers carpet's shadow players, which are built from a real profile.</li>
 * <li><b>The nearest non-fake player in the same dimension</b> - the practical meaning of "the one who summoned it".
 * A fake spawned from the console has no summoner reference anywhere in its data, so proximity is the only signal
 * left; it is also what Apotheosis itself uses for mob strength.</li>
 * </ol>
 *
 * <h2>Which sources may overwrite an existing tier</h2>
 * A fake's tier is saved to {@code playerdata} when it logs out, so a re-spawned fake starts with the tier it had last
 * time. Sources 1 and 2 are strong evidence - the name or profile literally identifies a player who is online right
 * now - and are allowed to overwrite that stale value, which is what makes "summoned by A, then by B" follow the
 * current summoner.
 * <p>
 * Source 3 is weak by comparison: it is a guess based on distance, and on a busy platform any nearby player would
 * qualify. It is therefore only used to <em>fill in</em> a missing or still-default tier, never to overwrite a
 * deliberate one. Without that split, a fake parked on a shared platform would silently change owner whenever a
 * different-tier player wandered past.
 *
 * <h2>Why this is opt-in</h2>
 * Inheriting a tier changes what a fake player spawns around itself, which is a gameplay decision a server owner may not
 * want. {@code fake_players_inherit_world_tier} gates the whole thing and defaults to off.
 */
public final class FakePlayerTierSync {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String LOG_TAG = "ApotheosisExpansion/FakeTier";

    private FakePlayerTierSync() {}

    /** Where the adopted tier came from, which decides whether it may replace an existing one. */
    private enum Source {
        /**
         * The player who ran the {@code /player ... spawn} command, captured by {@code CarpetPlayerCommandMixin}.
         * The strongest signal: a person explicitly asked for this fake, regardless of what the fake is called.
         */
        SUMMONER,
        /** The fake's name or profile matches an online player - {@code /player <name> spawn} for a real player's name. */
        IDENTITY,
        /** Nearest real player in the same dimension: a guess. */
        PROXIMITY,
        /** Nothing found. */
        NONE
    }

    /**
     * A resolved tier plus where it came from.
     *
     * @param tier   the tier to apply, or {@code null} when nothing was found
     * @param source how trustworthy the source is, which decides whether it may replace an existing tier
     * @param from   the player the tier was taken from; part of every log line because "inherited frontier" without
     *               "from whom" cannot be verified, which defeats the purpose of the diagnostic
     */
    private record Resolution(@Nullable WorldTier tier, Source source, String from) {
        static final Resolution NONE = new Resolution(null, Source.NONE, "-");
    }

    /**
     * Applies the summoner's world tier to {@code joining} when it looks like a fake player.
     * <p>
     * Called for every player that enters the world; real players are rejected by the cheap identity test before any
     * tier work happens.
     */
    public static void inheritOnJoin(MinecraftServer server, ServerPlayer joining) {
        if (joining != null && isFakePlayer(joining)) {
            // Logged before any gate so the log proves whether the mixin fired at all for this player, independently
            // of whether the feature happens to be enabled. Without this line a disabled switch and a mixin that
            // never ran look identical from the outside.
            LOGGER.info("[{}] Fake player '{}' joined (switch={})",
                LOG_TAG, joining.getGameProfile().getName(),
                com.tamamo.apotheosisexpansion.Config.fakePlayersInheritWorldTier());
        }
        if (server == null || joining == null) {
            return;
        }
        if (!com.tamamo.apotheosisexpansion.Config.fakePlayersInheritWorldTier()) {
            return;
        }
        if (!isFakePlayer(joining)) {
            return;
        }

        WorldTier current;
        try {
            current = WorldTier.getTier(joining);
        }
        catch (RuntimeException ex) {
            // No attachment yet. Treat as unset so the resolved tier gets written below.
            current = null;
        }

        Resolution resolved = resolve(server, joining);
        if (resolved.tier() == null || resolved.tier() == current) {
            LOGGER.info("[{}] Fake player '{}' kept tier {} (resolved {} from '{}' via {})",
                LOG_TAG, joining.getGameProfile().getName(), current, resolved.tier(), resolved.from(), resolved.source());
            return;
        }

        // A weak (proximity) signal must not undo a tier that was set deliberately - by a command, or by a previous
        // identity-based join. It may still replace the default, which is what a freshly spawned fake carries.
        if (resolved.source() == Source.PROXIMITY && current != null && current != WorldTier.HAVEN) {
            LOGGER.info("[{}] Fake player '{}' kept tier {} (proximity candidate '{}' = {} is not authoritative)",
                LOG_TAG, joining.getGameProfile().getName(), current, resolved.from(), resolved.tier());
            return;
        }

        LOGGER.info("[{}] Fake player '{}' tier {} -> {} (from '{}' via {})",
            LOG_TAG, joining.getGameProfile().getName(), current, resolved.tier(), resolved.from(), resolved.source());
        try {
            WorldTier.setTier(joining, resolved.tier());
        }
        catch (RuntimeException ex) {
            // setTier also pushes a ClientboundPacket to the player and re-runs the player-target tier augments. A fake
            // player's connection is a dummy, so either step can fail in ways a real login never would. The attachment
            // write itself happens first, so the tier the spawn logic needs is already in place - log and carry on
            // rather than turning a cosmetic packet failure into a failed join.
            LOGGER.warn("[{}] Fake player '{}' applied tier {} but setTier reported: {}",
                LOG_TAG, joining.getGameProfile().getName(), resolved.tier(), ex.toString());
        }
    }

    /** Finds the tier this fake player should adopt, together with how trustworthy that source is. */
    private static Resolution resolve(MinecraftServer server, ServerPlayer fake) {
        String name = fake.getGameProfile().getName();
        UUID id = fake.getUUID();

        // 0) The recorded summoner from the /player command itself. This is the only source that identifies an actual
        //    person rather than an inference, and it is what makes a fake named differently from its owner resolve
        //    correctly. Populated by CarpetPlayerCommandMixin; absent when the fake came from anywhere else.
        UUID summonerId = FakePlayerSummonTracker.claim(server, name);
        if (summonerId != null) {
            ServerPlayer summoner = server.getPlayerList().getPlayer(summonerId);
            if (summoner != null && !isFakePlayer(summoner)) {
                return new Resolution(WorldTier.getTier(summoner), Source.SUMMONER, summoner.getGameProfile().getName());
            }
        }

        // 1 + 2) Identity by name or profile: covers "/player <an online player's name> spawn".
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online == fake || isFakePlayer(online)) {
                continue;
            }
            GameProfile profile = online.getGameProfile();
            if (profile.getName().equalsIgnoreCase(name) || online.getUUID().equals(id)) {
                return new Resolution(WorldTier.getTier(online), Source.IDENTITY, online.getGameProfile().getName());
            }
        }

        // 3) Proximity, used only to fill in an unset/default tier.
        //
        // getNearestPlayer returns only the single closest player, and on a platform full of fakes that is almost
        // always another fake - asking it once and rejecting the answer would silently find nothing. So the whole
        // player list is scanned instead and the closest REAL player wins.
        if (fake.level() instanceof net.minecraft.server.level.ServerLevel level) {
            ServerPlayer closest = null;
            double closestDist = Double.MAX_VALUE;
            for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
                if (candidate == fake || isFakePlayer(candidate)) {
                    continue;
                }
                if (candidate.level() != level) {
                    continue;
                }
                double dist = candidate.distanceToSqr(fake);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = candidate;
                }
            }
            if (closest != null) {
                return new Resolution(WorldTier.getTier(closest), Source.PROXIMITY, closest.getGameProfile().getName());
            }
        }

        return Resolution.NONE;
    }

    /**
     * Whether this player is a carpet fake rather than a real connection.
     * <p>
     * Checked by class name instead of a direct reference so this mod compiles and runs with or without carpet
     * installed. Carpet names the class {@code carpet.patches.EntityPlayerMPFake}; other fake-player mods conventionally
     * put "Fake" (or "Shadow") in the simple name, which the superclass walk covers without hard-coding another
     * package.
     */
    public static boolean isFakePlayer(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.getClass().getName().startsWith("carpet.patches.EntityPlayerMPFake")) {
            return true;
        }
        for (Class<?> c = player.getClass(); c != null && c != ServerPlayer.class; c = c.getSuperclass()) {
            String simple = c.getSimpleName();
            if (simple.contains("Fake") || simple.contains("Shadow")) {
                return true;
            }
        }
        return false;
    }
}
