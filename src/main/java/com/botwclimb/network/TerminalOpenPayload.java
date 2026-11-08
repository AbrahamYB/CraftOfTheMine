package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Server to client: the player used a terminal; open its screen. {@code claimed} has one bit per plot slot, {@code here} is
 * the slot of the plot this terminal belongs to (-1 if none), {@code inPlot} says the terminal stands in the plot dimension.
 */
public record TerminalOpenPayload(BlockPos pos, int claimed, int here, boolean inPlot, int open, List<Host> hosts) implements CustomPacketPayload {
    /** A friend whose plot (slot) is open to join: who they are (for the skin face) and what to call them. */
    public record Host(UUID id, String name, int slot) {}

    public static final Type<TerminalOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "terminal_open"));
    public static final StreamCodec<FriendlyByteBuf, TerminalOpenPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeVarInt(p.claimed);
                buf.writeVarInt(p.here + 1);
                buf.writeBoolean(p.inPlot);
                buf.writeVarInt(p.open);
                buf.writeVarInt(p.hosts.size());
                for (Host h : p.hosts) {
                    buf.writeUUID(h.id);
                    buf.writeUtf(h.name);
                    buf.writeVarInt(h.slot);
                }
            },
            buf -> {
                var pos = buf.readBlockPos();
                int claimed = buf.readVarInt(), here = buf.readVarInt() - 1;
                boolean inPlot = buf.readBoolean();
                int open = buf.readVarInt();
                int n = buf.readVarInt();
                if (n < 0 || n > 16) throw new io.netty.handler.codec.DecoderException("too many hosts: " + n);
                var hosts = new java.util.ArrayList<Host>(n);
                for (int i = 0; i < n; i++) hosts.add(new Host(buf.readUUID(), buf.readUtf(32), buf.readVarInt()));
                return new TerminalOpenPayload(pos, claimed, here, inPlot, open, hosts);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
