package com.botwclimb.client.shrine.totk;

import com.botwclimb.BotwClimb;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Hands the render events the ride needs to TotkRide: the void over the HUD (and over the loading screen), and the field of view. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class TotkEvents {
    private TotkEvents() {}

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        TotkRide.hud(com.botwclimb.client.ClientCompat.gui(event), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ReceivingLevelScreen) TotkRide.hud(com.botwclimb.client.ClientCompat.gui(event), event.getPartialTick());
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        event.setFOV(TotkRide.fov((float) event.getFOV(), (float) event.getPartialTick()));
    }
}
