package com.botwclimb.network;

import com.botwclimb.BotwClimb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to its own player: the saved values the HUD and menus show (learned fabrics, stamina upgrades, altar credit). NeoForge's
 * attachment syncing is not there in every Minecraft version this mod is built for, so the mod sends them itself, whenever one changes.
 */
public record PlayerStatePayload(int fabricUnlocks, int staminaUpgrades, float altarCredit) implements CustomPacketPayload {
    public static final Type<PlayerStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "player_state"));
    public static final StreamCodec<FriendlyByteBuf, PlayerStatePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.fabricUnlocks);
                buf.writeVarInt(p.staminaUpgrades);
                buf.writeFloat(p.altarCredit);
            },
            buf -> new PlayerStatePayload(buf.readVarInt(), buf.readVarInt(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
