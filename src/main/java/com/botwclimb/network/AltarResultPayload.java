package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: how the offer went. {@code finale} is set when this offer was the last upgrade. */
public record AltarResultPayload(boolean ok, String reason, boolean finale) implements CustomPacketPayload {
    public static final Type<AltarResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "altar_result"));
    public static final StreamCodec<FriendlyByteBuf, AltarResultPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.ok);
                buf.writeUtf(p.reason, 32);
                buf.writeBoolean(p.finale);
            },
            buf -> new AltarResultPayload(buf.readBoolean(), buf.readUtf(32), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
