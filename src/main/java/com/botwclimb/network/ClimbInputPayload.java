package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbState;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server, one byte: what the client believes its climb state and wall direction are.
 * Sent only when one of them changes. The server validates the claim, it never trusts it.
 */
public record ClimbInputPayload(ClimbState state, Direction dir) implements CustomPacketPayload {
    public static final Type<ClimbInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "input"));

    public static final StreamCodec<FriendlyByteBuf, ClimbInputPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeByte(p.state.ordinal() | (p.dir.get2DDataValue() << 2)),
            buf -> {
                int b = buf.readByte();
                return new ClimbInputPayload(ClimbState.byId(b & 3), Direction.from2DDataValue((b >> 2) & 3));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
