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
    public static final int STALE = 5;
    /** The most mobs one packet lists; the server never sends more and the client refuses more. */
    public static final int MAX_IDS = 512;
    /** The server repeats the list this often while it is not empty, so a lost packet cannot leave a mob slowed on a screen. */
    public static final int KEEPALIVE = 20;

    // ---- other players, which cannot be slowed on the server: the shooter's screen draws them late and the server rewinds hits to match
    /** Visual playback speed of other players while bullet time runs (10% of normal), and the speed they catch up at afterwards. */
    public static final float SLOW = 0.1f, CATCH_UP = 5f;
    /** The picture falls behind the real world by this many ticks per tick while slowed, and gains CATCH_UP - 1 per tick while catching up. */
    public static final float LAG_GROWTH = 1f - SLOW, LAG_CATCH_UP = CATCH_UP - 1f;
    /** The client keeps this many ticks of every nearby player (30 s), so the picture can lag at most RING - 2 ticks. */
    public static final int CLIENT_RING = 600, MAX_LAG = CLIENT_RING - 2;
    /** The server keeps a little more than the client can look back, to cover the ping on top of the lag. */
    public static final int SERVER_RING = CLIENT_RING + 40;

    private BulletConstants() {}

    /**
     * How far behind the real world a player's picture is {@code age} ticks after they last reported {@code lag}: still growing
     * while reports keep coming (the first 2 ticks), then closing fast once they stop (bullet time over, the picture catches up).
     */
    public static int extrapolateLag(int lag, long age) {
        double l = age <= 2 ? lag + LAG_GROWTH * age : lag + LAG_GROWTH * 2 - LAG_CATCH_UP * (age - 2);
        return (int) Math.max(0, Math.min(MAX_LAG, Math.round(l)));
    }
}
