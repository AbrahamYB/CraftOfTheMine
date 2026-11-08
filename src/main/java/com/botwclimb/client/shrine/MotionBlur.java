package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The motion blur of a fast terminal ride (not on Minecraft 1.21.2 and later yet: the post effect format changed there): one full-screen pass (shaders/program/motion_blur.fsh) run over the finished world,
 * and only while the rider is moving fast, so the rest of the time it costs nothing. If anything about it fails (a shader
 * pack that replaces the pipeline, say) it switches itself off for the session instead of breaking rendering.
 */
// newer versions have no listener here, and the event bus refuses a subscriber class without one
//? if <1.21.2
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class MotionBlur {
    private static final ResourceLocation CHAIN = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "shaders/post/motion_blur.json");
    private static PostChain chain;
    private static int width, height;
    private static boolean failed;

    private MotionBlur() {}

    //? if <1.21.2 {
    @SubscribeEvent
    public static void apply(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || failed) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float strength = com.botwclimb.client.ClientConfig.style() == com.botwclimb.client.ClientConfig.RideStyle.TOTK ? com.botwclimb.client.shrine.totk.TotkRide.blur(partial)
                : LaunchEffects.ownSpeed(Minecraft.getInstance(), partial) * com.botwclimb.client.ClientConfig.motionBlur();
        if (strength < 0.05f) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN);
                width = height = 0;
            }
            if (w != width || h != height) {
                chain.resize(w, h);
                width = w;
                height = h;
            }
            chain.setUniform("Strength", strength); // already scaled by the 0..100% setting (75% is the look I tuned)
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            chain.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableDepthTest();
        } catch (Exception | LinkageError e) {
            failed = true;
            BotwClimb.LOGGER.warn("The ride's motion blur is off for this session", e);
        }
    }
    //?}
}
