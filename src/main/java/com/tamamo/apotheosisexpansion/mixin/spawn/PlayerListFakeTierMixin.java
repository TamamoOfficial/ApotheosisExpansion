package com.tamamo.apotheosisexpansion.mixin.spawn;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tamamo.apotheosisexpansion.spawn.FakePlayerTierSync;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;

/**
 * Gives carpet-style fake players the summoner's world tier as they enter the world.
 *
 * <h2>Why {@code placeNewPlayer}</h2>
 * Every player that joins - real connection, carpet {@code /player <name> spawn}, or carpet shadow - goes through
 * {@code PlayerList.placeNewPlayer}. Hooking it once therefore covers every fake-player flavour without depending on
 * carpet's own factory methods, and without a third-party mixin target: this is a vanilla method, so the patch keeps
 * working on mods this project knows nothing about.
 *
 * <h2>Why {@code TAIL}</h2>
 * The attachment write must happen after the player is fully registered, because {@code WorldTier.setTier} calls
 * {@code PacketDistributor.sendToPlayer} for the tier update and iterates the tier augments for players. Doing that on
 * a half-constructed {@code ServerPlayer} - before {@code placeNewPlayer} finished wiring the connection - would throw.
 *
 * <h2>Cost</h2>
 * {@link FakePlayerTierSync#inheritOnJoin} rejects real players with a string prefix test before touching any tier
 * data, so a normal login pays one class-name comparison and nothing else.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListFakeTierMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void apotheosisExpansion$syncFakePlayerTier(
        Connection connection,
        ServerPlayer player,
        CommonListenerCookie cookie,
        CallbackInfo ci
    ) {
        // Server may be null very early during boot; the helper guards that itself.
        FakePlayerTierSync.inheritOnJoin(player == null ? null : player.getServer(), player);
    }
}
