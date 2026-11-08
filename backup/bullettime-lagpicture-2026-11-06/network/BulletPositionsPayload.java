package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server to client during bullet time: the real position and body yaw of nearby mobs that moved or turned. */
public record BulletPositionsPayload(List<Snap> snaps) implements CustomPacketPayload {
    public record Snap(int id, double x, double y, double z, float yaw) {}

    /** The server sends at most 32 per tick; anything far above that is a broken or hostile sender. */
    public static final int MAX_SNAPS = 256;

    public static final Type<BulletPositionsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "bullet_positions"));
    public static final StreamCodec<FriendlyByteBuf, BulletPositionsPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.snaps.size());
                for (Snap s : p.snaps) {
                    buf.writeVarInt(s.id);
                    buf.writeDouble(s.x);
                    buf.writeDouble(s.y);
                    buf.writeDouble(s.z);
                    buf.writeFloat(s.yaw);
                }
            },
            buf -> {
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_SNAPS) throw new io.netty.handler.codec.DecoderException("too many bullet positions: " + n);
                List<Snap> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    out.add(new Snap(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
                }
                return new BulletPositionsPayload(out);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
