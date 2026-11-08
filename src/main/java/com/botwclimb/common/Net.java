package com.botwclimb.common;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sending to players that may not have the mod. The payloads are registered as optional so a player without the mod can still join,
 * but NeoForge throws when a payload is sent to a connection that does not know it; everything the server sends goes through here.
 */
public final class Net {
    /** How far from a player another player is told about them (the clients ignore what they do not know, so a little too far is harmless). */
    private static final double WATCH_RANGE_SQ = 160.0 * 160.0;

    private Net() {}

    public static void toPlayer(ServerPlayer p, CustomPacketPayload payload) {
        if (p.connection != null && p.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(p, payload);
    }

    /** To the players who can see {@code origin} (the ones close by in its level), and to {@code origin} too when asked. */
    public static void toWatchers(ServerPlayer origin, CustomPacketPayload payload, boolean includeSelf) {
        ServerLevel level = Compat.level(origin);
        for (ServerPlayer v : level.players()) {
            if (v == origin ? !includeSelf : v.distanceToSqr(origin) > WATCH_RANGE_SQ) continue;
            toPlayer(v, payload);
        }
    }
}
