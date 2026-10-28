package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.shrine.PlotData;
import com.botwclimb.common.shrine.PlotTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the terminal screen's button. {@code target} is a plot slot, or {@link PlotTravel#RETURN}. The server checks it all again. */
public record TerminalActionPayload(BlockPos pos, int target) implements CustomPacketPayload {
    public static final Type<TerminalActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "terminal_action"));
    public static final StreamCodec<FriendlyByteBuf, TerminalActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeVarInt(p.target + 1);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int target = buf.readVarInt() - 1;
                if (target < PlotTravel.RETURN || target >= PlotData.SLOTS && (target < PlotTravel.TOGGLE_OPEN || target >= PlotTravel.TOGGLE_OPEN + PlotData.SLOTS) && (target < PlotTravel.JOIN || target >= PlotTravel.JOIN + PlotTravel.MAX_HOSTS)) throw new io.netty.handler.codec.DecoderException("bad terminal target: " + target);
                return new TerminalActionPayload(pos, target);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
