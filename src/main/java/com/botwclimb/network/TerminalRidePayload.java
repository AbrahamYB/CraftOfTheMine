package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a player's terminal ride changed phase (0 = ended). {@code startY} is the ground the mud forms from; {@code kind} is 0 for a ride up into the sky, 1 for one that sinks down through the ground. */
public record TerminalRidePayload(int entityId, int phase, float startY, int kind) implements CustomPacketPayload {
    public static final Type<TerminalRidePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "terminal_ride"));
    public static final StreamCodec<FriendlyByteBuf, TerminalRidePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeByte(p.phase);
                buf.writeFloat(p.startY);
                buf.writeByte(p.kind);
            },
            buf -> new TerminalRidePayload(buf.readVarInt(), buf.readByte(), buf.readFloat(), buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
