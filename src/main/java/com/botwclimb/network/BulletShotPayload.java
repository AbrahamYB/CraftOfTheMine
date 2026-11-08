package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server every tick of bullet time: the heartbeat that keeps it running, and how many ticks behind the real world the other players' drawn picture is. */
public record BulletShotPayload(int lag) implements CustomPacketPayload {
    public static final Type<BulletShotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "bullet_shot"));
    public static final StreamCodec<FriendlyByteBuf, BulletShotPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.lag), buf -> new BulletShotPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
