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
 * Server to client: the player used a terminal; open its screen. {@code claimed}: the player has a plot; {@code here}: this
 * terminal is on it; {@code inPlot}: the terminal stands in the plot dimension; {@code open}: friends may join the player's plot.
 */
public record TerminalOpenPayload(BlockPos pos, boolean claimed, boolean here, boolean inPlot, boolean open, List<Host> hosts) implements CustomPacketPayload {
    /** A friend whose plot is open to join: who they are (for the skin face) and what to call them. */
    public record Host(UUID id, String name) {}

    public static final Type<TerminalOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "terminal_open"));
    public static final StreamCodec<FriendlyByteBuf, TerminalOpenPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeBoolean(p.claimed);
                buf.writeBoolean(p.here);
                buf.writeBoolean(p.inPlot);
                buf.writeBoolean(p.open);
                buf.writeVarInt(p.hosts.size());
                for (Host h : p.hosts) {
                    buf.writeUUID(h.id);
                    buf.writeUtf(h.name);
                }
            },
            buf -> {
                var pos = buf.readBlockPos();
                boolean claimed = buf.readBoolean(), here = buf.readBoolean(), inPlot = buf.readBoolean(), open = buf.readBoolean();
                int n = buf.readVarInt();
                if (n < 0 || n > com.botwclimb.common.shrine.PlotTravel.MAX_HOSTS) throw new io.netty.handler.codec.DecoderException("too many hosts: " + n);
                var hosts = new java.util.ArrayList<Host>(n);
                for (int i = 0; i < n; i++) hosts.add(new Host(buf.readUUID(), buf.readUtf(32)));
                return new TerminalOpenPayload(pos, claimed, here, inPlot, open, hosts);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
