package com.botwclimb.common.bullettime;

/** Numbers the server's slowed mobs and the client's smoothing must agree on. */
public final class BulletConstants {
    /**
     * A slowed mob gets one tick in every PERIOD, about 14% speed. Odd on purpose, so its turn never lines up with the 2-, 4- and
     * 20-tick rhythms vanilla runs things on.
     */
    public static final int PERIOD = 7;
    /** What one shot costs: 21.5% of a 750 wheel. */
    public static final float SHOT_COST = 161.25f;
    /** How far from the shooter mobs are slowed, in blocks. */
    /** What the server allows one player of bullet time (a client can be modified): ticks it may run before it must rest, and the ticks of rest that give one back. */
    public static final int SERVER_BUDGET = 400;
    public static final float SERVER_REFILL = 0.5f;
    public static final int RADIUS = 128; // 8 chunks around the shooter
    /** A mob stays slowed this many ticks after it was last in range, so one missed scan never lets it jump. */
    public static final int GRACE = 2;
    /** A shooter counts as in bullet time while their heartbeat is at most this many ticks old. */
    public static final int STALE = 10;
    /** The most mobs one packet lists; the server never sends more and the client refuses more. */
    public static final int MAX_IDS = 512;
    /** The server repeats the list this often while it is not empty, so a lost packet cannot leave a mob slowed on a screen. */
    public static final int KEEPALIVE = 20;

    // ---- other players, which cannot be slowed on the server: the shooter's screen draws them late
    /** Visual playback speed of other players while bullet time runs (10% of normal), and the speed they catch up at afterwards. */
    public static final float SLOW = 0.1f, CATCH_UP = 5f;
    /** The client keeps this many ticks of every nearby player (30 s). */
    public static final int CLIENT_RING = 600;

    private BulletConstants() {}
}
