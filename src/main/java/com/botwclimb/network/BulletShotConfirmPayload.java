package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a bullet-time arrow really left the bow, so the shot is paid for now. */
public record BulletShotConfirmPayload() implements CustomPacketPayload {
    public static final Type<BulletShotConfirmPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "bullet_shot_confirm"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, BulletShotConfirmPayload> CODEC =
            StreamCodec.unit(new BulletShotConfirmPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
