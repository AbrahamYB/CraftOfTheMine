package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: a rider's game has finished loading the world it was moved to (its dimension's key, hashed), so the drop home may start. */
public record RideLoadedPayload(int dimension) implements CustomPacketPayload {
    public static final Type<RideLoadedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "ride_loaded"));
    public static final StreamCodec<FriendlyByteBuf, RideLoadedPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeInt(p.dimension), buf -> new RideLoadedPayload(buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
