package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server whenever the wheel's level changed: what the server saves for the next join (see StaminaPayload). The server's own
 * checks never rely on it.
 */
public record StaminaReportPayload(float stamina, boolean exhausted) implements CustomPacketPayload {
    public static final Type<StaminaReportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "stamina_report"));
    public static final StreamCodec<FriendlyByteBuf, StaminaReportPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.stamina);
                buf.writeBoolean(p.exhausted);
            },
            buf -> new StaminaReportPayload(buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
