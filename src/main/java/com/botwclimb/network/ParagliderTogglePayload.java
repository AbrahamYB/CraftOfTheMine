package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: open (true) or close (false) the paraglider. One byte, sent once per chord press. */
public record ParagliderTogglePayload(boolean open) implements CustomPacketPayload {
    public static final Type<ParagliderTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "glide_toggle"));
    public static final StreamCodec<FriendlyByteBuf, ParagliderTogglePayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.open), buf -> new ParagliderTogglePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
