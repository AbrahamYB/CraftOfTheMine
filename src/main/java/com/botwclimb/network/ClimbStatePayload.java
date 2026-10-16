package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbState;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the validated climb state of one player. Tracking clients use it to pose that player.
 * {@code correction} tells the owning client its own claim was rejected.
 */
public record ClimbStatePayload(int entityId, ClimbState state, Direction dir, boolean correction)
        implements CustomPacketPayload {
    public static final Type<ClimbStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "state"));

    public static final StreamCodec<FriendlyByteBuf, ClimbStatePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeByte(p.state.ordinal() | (p.dir.get2DDataValue() << 2) | (p.correction ? 16 : 0));
            },
            buf -> {
                int id = buf.readVarInt();
                int b = buf.readByte();
                return new ClimbStatePayload(id, ClimbState.byId(b & 3), Direction.from2DDataValue((b >> 2) & 3),
                        (b & 16) != 0);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
