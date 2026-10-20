package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Client to server: buy the next stamina upgrade with these inventory stacks (slot, count). Or, with no entries and
 * {@code refund} set, pay the leftover credit back as that item. The server checks everything again.
 */
public record AltarOfferPayload(BlockPos pos, List<Entry> entries, ResourceLocation refund) implements CustomPacketPayload {
    public record Entry(int slot, int count) {}

    public static final int MAX_ENTRIES = 9;
    public static final ResourceLocation NO_REFUND = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "none");

    public static final Type<AltarOfferPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "altar_offer"));
    public static final StreamCodec<FriendlyByteBuf, AltarOfferPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeVarInt(p.entries.size());
                for (Entry e : p.entries) {
                    buf.writeVarInt(e.slot);
                    buf.writeVarInt(e.count);
                }
                buf.writeResourceLocation(p.refund);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_ENTRIES) throw new io.netty.handler.codec.DecoderException("too many altar entries: " + n);
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(new Entry(buf.readVarInt(), buf.readVarInt()));
                return new AltarOfferPayload(pos, list, buf.readResourceLocation());
            });

    public boolean isRefund() {
        return !refund.equals(NO_REFUND);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
