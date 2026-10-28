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
                buf.writeCollection(p.hosts, (b, h) -> {
                    b.writeUUID(h.id);
                    b.writeUtf(h.name);
                    b.writeVarInt(h.slot);
                });
            },
            buf -> new TerminalOpenPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt(), buf.readList(b -> new Host(b.readUUID(), b.readUtf(32), b.readVarInt()))));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
