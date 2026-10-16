package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: whether a player's paraglider is open. {@code correction} overrides the owner's own prediction. */
public record ParagliderStatePayload(int entityId, boolean gliding, boolean correction, int fabric) implements CustomPacketPayload {
    public static final Type<ParagliderStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "glide_state"));
    public static final StreamCodec<FriendlyByteBuf, ParagliderStatePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeByte((p.gliding ? 1 : 0) | (p.correction ? 2 : 0));
                buf.writeByte(p.fabric);
            },
            buf -> {
                int id = buf.readVarInt();
                int b = buf.readByte();
                return new ParagliderStatePayload(id, (b & 1) != 0, (b & 2) != 0, buf.readByte());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
