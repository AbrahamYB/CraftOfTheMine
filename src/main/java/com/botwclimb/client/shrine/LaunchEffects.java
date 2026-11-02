package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * What a fast terminal ride feels like to the rider: the field of view opens up, and pale lines rush past the edges of the screen. (There is no real motion blur: that would
 * need a post-processing shader, which clashes with the shader mods players run.)
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class LaunchEffects {

    private LaunchEffects() {}

    /** 0..1: how fast the player's own ride is going right now (either way). */
    static float ownSpeed(Minecraft mc, float partial) {
        if (mc.player == null || mc.level == null) return 0f;
        MudHandClient.State s = MudHandClient.get(mc.player.getId());
        if (s == null || s.phase() != 2 && s.phase() != 4) return 0f;
        return MudHandClient.anim(s, mc.player, partial, mc.level.getGameTime()).speed();
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (com.botwclimb.client.ClientConfig.style() == com.botwclimb.client.ClientConfig.RideStyle.TOTK) {
            event.setFOV(com.botwclimb.client.shrine.totk.TotkRide.fov((float) event.getFOV(), (float) event.getPartialTick()));
            return;
        }
        float f = ownSpeed(Minecraft.getInstance(), (float) event.getPartialTick());
        if (f > 0f) event.setFOV((float) (event.getFOV() * (1 + 0.32 * f)));
    }

    @SubscribeEvent
    public static void lines(RenderGuiEvent.Post event) {
        if (com.botwclimb.client.ClientConfig.style() == com.botwclimb.client.ClientConfig.RideStyle.TOTK || !com.botwclimb.client.ClientConfig.speedLines()) return;
        Minecraft mc = Minecraft.getInstance();
        float f = ownSpeed(mc, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (f < 0.12f) return;
        MudHandClient.State s = MudHandClient.get(mc.player.getId());
        boolean rising = s.rising(); // lines drift down the screen while rising, up while falling or sinking
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        double time = (mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.09;
        int alpha = (int) (70 * Mth.clamp(f, 0f, 1f)) << 24;
        for (int i = 0; i < 26; i++) {
            float side = hash(i, 1); // most lines sit toward the edges, where speed is felt
            float x = side < 0.5f ? side * 0.5f : 1 - (1 - side) * 0.5f;
            float length = h * (0.12f + 0.3f * hash(i, 2));
            double along = (hash(i, 3) + time * (0.8 + hash(i, 4))) % 1.0;
            float y = (float) ((rising ? along : 1 - along) * (h + length)) - length;
            int px = (int) (x * w), width = hash(i, 5) < 0.3f ? 2 : 1;
            int lead = alpha | 0xFFFFFF, trail = 0x00FFFFFF; // the end it moves toward is bright, the tail fades
            g.fillGradient(px, (int) y, px + width, (int) (y + length), rising ? trail : lead, rising ? lead : trail);
        }
    }

    private static float hash(int i, int salt) {
        int n = i * 73856093 ^ salt * 19349663;
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
    }
}
