package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.shrine.TerminalRide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The cloud the terminal ride passes through. While the rider is held at the top the view whites out; the world change happens
 * behind it (including the game's own "loading terrain" screen, which is covered too), and it clears once the rider is
 * coming down on the other side.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class CloudVeil {
    /** The server holds the rider this long at the top (TerminalRide.HOLD); if the rise was cut short the white closes in over it instead. */
    private static final float CLOSE_TICKS = 6f;
    /** The white starts to close in this high above where the ride began, and is complete at the top of the rise. */
    private static final float VEIL_FROM = 36f, SINK_FROM = 3.5f, SINK_FULL = 3f; // (sinking: starts this far below the ground and is white 3 blocks later)
    private static final long CLEAR_MS = 700;
    private static long fullAt = -CLEAR_MS;

    private CloudVeil() {}

    private static float alpha(Minecraft mc, float partialTick) {
        if (mc.player == null || mc.level == null) return 0f;
        MudHandClient.State s = MudHandClient.get(mc.player.getId());
        if (s == null) return 0f;
        long now = net.minecraft.Util.getMillis();
        float a = 0f;
        if (mc.screen instanceof ReceivingLevelScreen) a = 1f;
        else if (s.phase() == 2 || s.phase() == 3) {
            float height = (float) (Mth.lerp(partialTick, mc.player.yo, mc.player.getY()) - s.startY());
            float climb = Mth.clamp((height - VEIL_FROM) / (TerminalRide.RISE_HEIGHT - VEIL_FROM), 0f, 1f);
            a = climb * climb;
            if (s.kind() == 1) a = Mth.clamp((-height - SINK_FROM) / SINK_FULL, 0f, 1f); // quick, so the camera never shows the inside of the ground
            if (s.phase() == 3) a = Math.max(a, Mth.clamp((mc.level.getGameTime() + partialTick - s.phaseSince()) / CLOSE_TICKS, 0f, 1f));
        } else if (s.phase() == 4) a = 1f - Mth.clamp((now - fullAt) / (float) CLEAR_MS, 0f, 1f);
        if (a >= 1f) fullAt = now;
        return a;
    }

    private static void draw(GuiGraphics g, float a) {
        if (a <= 0.001f) return;
        int alpha = (int) (Mth.clamp(a, 0f, 1f) * 255) << 24;
        g.fillGradient(0, 0, g.guiWidth(), g.guiHeight(), alpha | 0xD6E6F6, alpha | 0xFFFFFF);
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        if (com.botwclimb.client.ClientConfig.style() == com.botwclimb.client.ClientConfig.RideStyle.TOTK) {
            com.botwclimb.client.shrine.totk.TotkRide.hud(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
            return;
        }
        draw(event.getGuiGraphics(), alpha(Minecraft.getInstance(), event.getPartialTick().getGameTimeDeltaPartialTick(false)));
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Render.Post event) {
        if (com.botwclimb.client.ClientConfig.style() == com.botwclimb.client.ClientConfig.RideStyle.TOTK) {
            if (event.getScreen() instanceof ReceivingLevelScreen) com.botwclimb.client.shrine.totk.TotkRide.hud(event.getGuiGraphics(), event.getPartialTick());
            return;
        }
        if (event.getScreen() instanceof ReceivingLevelScreen) draw(event.getGuiGraphics(), alpha(Minecraft.getInstance(), event.getPartialTick()));
    }
}
