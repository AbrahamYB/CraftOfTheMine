package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The wheel's level, kept across a disconnect. Server to client once on joining: what was left when the player last left (-1 = nothing
 * saved). Client to server whenever the level changed: what the server saves for that. The server's own checks never rely on it.
 */
public record StaminaPayload(float stamina, boolean exhausted) implements CustomPacketPayload {
    public static final Type<StaminaPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "stamina"));
    public static final StreamCodec<FriendlyByteBuf, StaminaPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.stamina);
                buf.writeBoolean(p.exhausted);
            },
            buf -> new StaminaPayload(buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
