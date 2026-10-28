package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.shrine.PlotWorld;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Anyone in the plot dimension who is not on a plot's island sees nothing of it: the fog closes in to arm's length. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class PlotVeil {
    private PlotVeil() {}

    private static boolean outside() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.level.dimension() == PlotWorld.DIM && !PlotWorld.inSquare(mc.player.getX(), mc.player.getZ());
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        if (!outside()) return;
        event.setNearPlaneDistance(0f);
        event.setFarPlaneDistance(3f);
        event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void colour(ViewportEvent.ComputeFogColor event) {
        if (!outside()) return;
        event.setRed(0f);
        event.setGreen(0f);
        event.setBlue(0f);
    }
}
