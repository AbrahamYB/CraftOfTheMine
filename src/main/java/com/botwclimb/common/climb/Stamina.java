package com.botwclimb.common.climb;

import com.botwclimb.common.paraglider.ParagliderConstants;
import net.minecraft.util.Mth;

import static com.botwclimb.common.climb.ClimbConstants.*;

/**
 * The stamina rules in one place. Client side: {@link #take} and {@link #exhaust} are what climbing, gliding and the bow
 * all use, so running out always means the same thing. Server side: {@link #serverStep} / {@link #serverSpend} keep a
 * deliberately generous second account, so a modified client cannot climb or glide forever, while a normal one (which
 * drains faster and recovers slower) never runs into it.
 */
public final class Stamina {
    /** The server charges this share of what the client does, and gives back more, so it never rejects a legal player. */
    private static final float SERVER_DRAIN = 0.6f, SERVER_REGEN = 1.5f;

    private Stamina() {}

    /** Takes up to {@code amount}. Taking the last of it leaves you exhausted. Returns what was actually taken. */
    public static float take(ClimbData d, float amount) {
        float taken = Math.min(d.stamina, amount);
        d.stamina -= taken;
        d.staminaDelay = Math.max(d.staminaDelay, STAMINA_REGEN_DELAY);
        if (d.stamina <= 0f) exhaust(d);
        return taken;
    }

    /** Empty: no climbing, gliding or jumping until the wheel has refilled completely. */
    public static void exhaust(ClimbData d) {
        d.stamina = 0f;
        d.exhausted = true;
        d.staminaDelay = STAMINA_EXHAUST_DELAY;
        d.staminaFlash = 40;
    }

    // ------------------------------------------------------------------ server account

    /** One server tick: drains while hanging and moving on a wall or gliding, recovers otherwise. */
    public static void serverStep(ClimbData d, float max, boolean climbingMoving, boolean gliding) {
        serverStep(d, max, climbingMoving, gliding, true);
    }

    /** {@code grounded}: only a player on the ground (or flying) recovers, so closing the glider for a tick in mid-air buys nothing. */
    public static void serverStep(ClimbData d, float max, boolean climbingMoving, boolean gliding, boolean grounded) {
        if (d.sStamina < 0f || d.sStamina > max) d.sStamina = max;
        if (gliding) d.sStamina -= ParagliderConstants.PARAGLIDER_STAMINA_DRAIN_PER_TICK * SERVER_DRAIN;
        else if (climbingMoving) d.sStamina -= STAMINA_CLIMB_DRAIN * SERVER_DRAIN;
        else if (grounded && d.state == ClimbState.NONE) d.sStamina = Math.min(max, d.sStamina + STAMINA_REGEN * SERVER_REGEN);
        if (d.sStamina <= 0f) {
            d.sStamina = 0f;
            d.sExhausted = true;
        } else if (d.sExhausted && d.sStamina >= max) {
            d.sExhausted = false;
        }
    }

    /** A jump claim costs its share; it may empty the account (the next tick then refuses everything). */
    public static void serverSpend(ClimbData d, float cost) {
        d.sStamina = Mth.clamp(d.sStamina - cost * SERVER_DRAIN, 0f, Float.MAX_VALUE);
    }
}
