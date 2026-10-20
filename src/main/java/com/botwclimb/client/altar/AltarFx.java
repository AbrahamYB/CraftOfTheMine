package com.botwclimb.client.altar;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * How the altar's lights look to the local player. Nothing here is shared: every client works it out from its own
 * player's upgrades, so another player standing at the same altar sees their own progress, not yours.
 * Before the first upgrade everything is shadowed. From the first upgrade the runes light up and each upgrade lights one
 * quarter (4 on the outer ring, 4 on the inner one). When the last upgrade is bought, the lights stay green until the
 * menu is closed, then fade out, stay off for 5 seconds, and fade back in as the final colour (cyan) for good.
 */
public final class AltarFx {
    public static final int GREEN = 0x00F25A, FINAL = 0x0CDDE8, SHADOW = 0x175126; // the shadowed (lights off) green
    private static final float FADE_OUT = 20f, OFF = 100f, FADE_IN = 40f; // ticks: 1 s, 5 s, 2 s

    private static boolean finalePending; // the last upgrade was just bought and the menu is still open
    private static long finaleStart = Long.MIN_VALUE; // game time the menu was closed, when the lights go out

    private AltarFx() {}

    /** The offer that just completed the last upgrade. */
    public static void onFinale() {
        finalePending = true;
    }

    /** The altar menu closed: if the last upgrade was just bought, the finale starts now. */
    public static void onMenuClosed() {
        Minecraft mc = Minecraft.getInstance();
        if (finalePending && mc.level != null) {
            finalePending = false;
            finaleStart = mc.level.getGameTime();
        }
    }

    public static void reset() {
        finalePending = false;
        finaleStart = Long.MIN_VALUE;
    }

    /** What one lit element looks like: its colour and how bright (0 = off, drawn as nothing at all). */
    public record Look(int rgb, float level) {}

    public static int upgrades(LocalPlayer p) {
        return Mth.clamp(p.getData(BotwClimb.STAMINA_UPGRADES), 0, ClimbConstants.STAMINA_MAX_UPGRADES);
    }

    /** Is this quarter lit for the player? ring 0 = outer (upgrades 1-4), ring 1 = inner (upgrades 5-8). */
    public static boolean quarterLit(int upgrades, int ring, int quarter) {
        return upgrades - ring * 4 > quarter;
    }

    /** The colour and brightness every lit part (rings and runes) shows right now. */
    public static Look lit(int upgrades, float partialTick) {
        if (upgrades < ClimbConstants.STAMINA_MAX_UPGRADES) return new Look(GREEN, 1f);
        if (finalePending) return new Look(GREEN, 1f);
        if (finaleStart == Long.MIN_VALUE) return new Look(FINAL, 1f);
        Minecraft mc = Minecraft.getInstance();
        float t = mc.level == null ? 0f : (mc.level.getGameTime() - finaleStart) + partialTick;
        if (t < FADE_OUT) return new Look(GREEN, 1f - smooth(t / FADE_OUT));
        if (t < FADE_OUT + OFF) return new Look(FINAL, 0f);
        if (t < FADE_OUT + OFF + FADE_IN) return new Look(FINAL, smooth((t - FADE_OUT - OFF) / FADE_IN));
        finaleStart = Long.MIN_VALUE; // done: the final colour from now on
        return new Look(FINAL, 1f);
    }

    /** Is the whole block dark right now (the 5 seconds with the lights off, and the fades around them)? Shadows go out too. */
    public static boolean finaleActive() {
        return finaleStart != Long.MIN_VALUE;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}
