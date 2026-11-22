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
    public static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        TotkRide.sounds(mc);
        var s = mc.player == null ? null : com.botwclimb.client.shrine.MudHandClient.get(mc.player.getId());
        if (s != null) mc.player.resetFallDistance(); // the ride carries the player down without gravity; counted as a fall, letting go would play the fall's thud
        if (s != null && s.phase() == 4 && s.kind() == 1 && !(mc.screen instanceof ReceivingLevelScreen) && s.phaseSince() * 31 + mc.level.dimension().toString().hashCode() != loadedSent) { // coming home, the world is in view: the drop may start (said once per world, so a message sent before the move is said again after it)
            loadedSent = s.phaseSince() * 31 + mc.level.dimension().toString().hashCode();
            com.botwclimb.client.ClientCompat.sendToServer(new com.botwclimb.network.RideLoadedPayload(mc.level.dimension().toString().hashCode()));
        }
    }

    private static long loadedSent = -1;

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        TotkRide.hud(com.botwclimb.client.ClientCompat.gui(event), event.getPartialTick().getGameTimeDeltaPartialTick(false), false);
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ReceivingLevelScreen) TotkRide.hud(com.botwclimb.client.ClientCompat.gui(event), event.getPartialTick(), false);
    }

    /** The void goes under the whole HUD, so the hotbar's see-through slots show the void and not the world. */
    //? if >=1.21.8 {
    /*@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
    *///?} else {
    @EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    //?}
    public static final class Setup {
        @SubscribeEvent
        public static void layers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
            event.registerBelowAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "ride_void"), (g, delta) -> TotkRide.hud(g, delta.getGameTimeDeltaPartialTick(false), true));
        }
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        float d = TotkRide.shake();
        if (d != 0f) event.setPitch(event.getPitch() + d);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        event.setFOV(TotkRide.fov((float) event.getFOV(), (float) event.getPartialTick()));
    }
}
