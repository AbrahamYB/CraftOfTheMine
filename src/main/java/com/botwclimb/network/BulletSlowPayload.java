package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.bullettime.BulletConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client during bullet time: the ids of the mobs that are running slowed, so the client can draw them smoothly. */
public record BulletSlowPayload(int[] ids) implements CustomPacketPayload {
    public static final Type<BulletSlowPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "bullet_slow"));
    public static final StreamCodec<FriendlyByteBuf, BulletSlowPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.ids.length);
                for (int id : p.ids) buf.writeVarInt(id);
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > BulletConstants.MAX_IDS) throw new io.netty.handler.codec.DecoderException("too many slowed mobs: " + n);
                int[] ids = new int[n];
                for (int i = 0; i < n; i++) ids[i] = buf.readVarInt();
                return new BulletSlowPayload(ids);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
