package com.botwclimb.common.bullettime;

/** Numbers the client's slowed picture and the server's hit test must agree on. */
public final class BulletConstants {
    /** Visual playback speed of slowed characters while bullet time runs (10% of normal), and the speed they catch up at afterwards. */
    public static final float SLOW = 0.1f, CATCH_UP = 5f;
    /** The picture falls behind the real world by this many ticks per tick while slowed, and gains CATCH_UP - 1 per tick while catching up. */
    public static final float LAG_GROWTH = 1f - SLOW, LAG_CATCH_UP = CATCH_UP - 1f;
    /** What one shot costs: 21.5% of a 750 wheel. */
    public static final float SHOT_COST = 161.25f;
    /** The client keeps this many ticks of every nearby character (30 s), so the picture can lag at most RING - 2 ticks. */
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
