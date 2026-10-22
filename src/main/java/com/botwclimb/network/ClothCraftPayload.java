package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the player pressed Craft on a paraglider cloth in the fabric menu (an index into FabricRegistry). */
public record ClothCraftPayload(int index) implements CustomPacketPayload {
    public static final Type<ClothCraftPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "cloth_craft"));
    public static final StreamCodec<FriendlyByteBuf, ClothCraftPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeByte(p.index), buf -> new ClothCraftPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
