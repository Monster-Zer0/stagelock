package dev.stagelock.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

import java.util.UUID;

final class TestPlayers {

    private TestPlayers() {
    }

    /** Like {@code GameTestHelper#makeMockServerPlayerInLevel}, but in the given game mode instead of forced creative. */
    static ServerPlayer create(GameTestHelper helper, GameType mode) {
        var server = helper.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "tp-" + UUID.randomUUID().toString().substring(0, 8)), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // Act like a modded client that accepts every payload (ours, and other mods' such as KubeJS).
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5));
        player.moveTo(pos.x, pos.y, pos.z);
        return player;
    }

    /** Adds rules for a test batch on top of whatever datapacks and scripts loaded (minus the example pack). */
    static void addRules(java.util.Map<net.minecraft.resources.ResourceLocation, dev.stagelock.api.restriction.Restriction> rules) {
        var merged = new java.util.LinkedHashMap<>(dev.stagelock.core.restriction.RestrictionManager.server().rules());
        // The game test server enables every pack, including our opt-in example pack; keep its rules out of the way.
        merged.keySet().removeIf(id -> id.getNamespace().equals("stagelock_examples"));
        merged.putAll(rules);
        dev.stagelock.core.restriction.RestrictionManager.server().setRules(merged);
    }

    static void remove(ServerPlayer player) {
        player.getServer().getPlayerList().remove(player);
    }
}
