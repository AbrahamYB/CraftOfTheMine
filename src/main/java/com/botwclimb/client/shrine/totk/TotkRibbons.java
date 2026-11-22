package com.botwclimb.client.shrine.totk;

import com.botwclimb.BotwClimb;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the ribbons round riders going up after the clouds (the stage PlotOutline uses), so they need no depth of their own: drawn with the
 * player model they either cut pieces out of each other and the clouds, or let the clouds paint over them.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class TotkRibbons {
    private TotkRibbons() {}

    //? if >=26.2 {
    /*@SubscribeEvent
    public static void onSubmit(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event) {
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), TotkRide.bodyType(),
                (pose, vc) -> TotkRide.ribbons(com.botwclimb.client.ClientCompat.poseStackOf(pose), vc, true));
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), TotkRide.glowType(),
                (pose, vc) -> TotkRide.ribbons(com.botwclimb.client.ClientCompat.poseStackOf(pose), vc, false));
    }
    *///?} else if >=1.21.8 {
    /*@SubscribeEvent
    public static void draw(RenderLevelStageEvent.AfterWeather event) {
        render(event);
    }
    *///?} else {
    @SubscribeEvent
    public static void draw(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) render(event);
    }
    //?}

    //? if >=26.2 {
    /*// 26.2 has no immediate buffers: the geometry is submitted from onSubmit
    *///?} else {
    private static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        com.botwclimb.client.Buffers buffers = com.botwclimb.client.ClientCompat.immediateBuffers(mc);
        var ps = com.botwclimb.client.ClientCompat.levelPose(event);
        TotkRide.ribbons(ps, buffers.getBuffer(TotkRide.bodyType()), true); // the coloured sheaths first, blended
        buffers.endBatch(TotkRide.bodyType());
        TotkRide.ribbons(ps, buffers.getBuffer(TotkRide.glowType()), false); // then the light on top
        buffers.endBatch(TotkRide.glowType());
    }
    //?}
}
