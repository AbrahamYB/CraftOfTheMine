package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the fabric the player picked in the inventory (an index into FabricRegistry). */
public record ParagliderFabricPayload(int index) implements CustomPacketPayload {
    public static final Type<ParagliderFabricPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "glide_fabric"));
    public static final StreamCodec<FriendlyByteBuf, ParagliderFabricPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeByte(p.index), buf -> new ParagliderFabricPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
