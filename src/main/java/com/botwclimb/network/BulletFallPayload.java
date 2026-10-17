package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: bullet time started (true) or ended (false), so the server slows the same fall. */
public record BulletFallPayload(boolean on) implements CustomPacketPayload {
    public static final Type<BulletFallPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "bullet_fall"));
    public static final StreamCodec<FriendlyByteBuf, BulletFallPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.on), buf -> new BulletFallPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
