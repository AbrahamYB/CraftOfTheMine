package com.botwclimb.client.altar;

import com.botwclimb.BotwClimb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Which look the altar shows as an item (in the hotbar, inventory, hand, a frame or on the ground): the local player's upgrades, 0 to 8,
 * picked by the item model (see tools/altar_item.py). It is the same state as the block's own glow, held still: green with that many
 * quarters lit. Without a player it is the unlit look.
 */
public final class AltarPhase {
    private AltarPhase() {}

    public static int value() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.hasData(BotwClimb.STAMINA_UPGRADES) ? AltarFx.upgrades(p) : 0;
    }

    //? if >=1.21.9 {
    /*public record Property() implements net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty {
        public static final com.mojang.serialization.MapCodec<Property> CODEC = com.mojang.serialization.MapCodec.unit(new Property());

        @Override
        public float get(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.ItemOwner owner, int seed) {
            return value();
        }

        @Override
        public com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty> type() {
            return CODEC;
        }
    }
    *///?} else if >=1.21.4 {
    /*public record Property() implements net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty {
        public static final com.mojang.serialization.MapCodec<Property> CODEC = com.mojang.serialization.MapCodec.unit(new Property());

        @Override
        public float get(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.entity.LivingEntity owner, int seed) {
            return value();
        }

        @Override
        public com.mojang.serialization.MapCodec<? extends net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty> type() {
            return CODEC;
        }
    }
    *///?}
}
