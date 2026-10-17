package com.botwclimb.common.paraglider;

/** Paraglider tuning. Speeds are blocks/tick, accelerations blocks/tick^2, angles degrees. */
public final class ParagliderConstants {
    private ParagliderConstants() {}

    public static final double DESCENT_SPEED = -0.145;        // BOTW ~3.2 m/s
    public static final double MAX_HORIZONTAL_SPEED = 0.312;   // 20% faster than the first tuning (0.26)
    public static final double HORIZONTAL_ACCELERATION = 0.03;
    public static final double HORIZONTAL_DECELERATION = 0.0216;
    /**
     * How long momentum carried over from Elytra flight lasts, by rocket flight duration 1 / 2 / 3. Vanilla average
     * rocket lifetimes are 25.5 : 35.5 : 45.5 ticks; stretched so tier 1 = 6 s. Tier sets TIME, not speed.
     */
    public static final int[] ROCKET_MOMENTUM_TICKS = {120, 167, 214};
    /** Highest speed carried over from the Elytra. shortcut: a plain cap, not tuned in play yet. */
    public static final double BOOST_SPEED_CAP = 1.2;
    /** How fast a fast fall is caught when the canopy opens (-2.5 -> -0.145 in about 5 ticks). */
    public static final double CATCH_RATE = 0.5;
    /** How fast an upward velocity settles back onto the descent speed. */
    public static final double SETTLE_RATE = 0.05;

    /** Shift + Space must be held this long (8 ticks = 0.4 s) to open or close the glider. */
    public static final int HOLD_TICKS = 8;

    /** A plain jump after falling this many blocks opens the glider instead of the Elytra. */
    public static final double OPEN_AFTER_FALL = 2.0;
    /** Gravity is this much weaker on everyone (-0.2 = falls 20% slower). */
    public static final double GRAVITY_MODIFIER = -0.2;

    public static final int DEPLOY_DURATION_TICKS = 5;
    public static final int CLOSE_DURATION_TICKS = 3;

    public static final float MAX_VISUAL_BANK_DEGREES = 15f;
    public static final float PASSIVE_SWAY_DEGREES = 1.5f;
    public static final float MAX_VISUAL_PITCH_DEGREES = 8f;

    /** The server keeps the glider open this many ticks of "on ground" before closing it (position lag). */
    public static final int SERVER_GROUND_GRACE = 3;
}
