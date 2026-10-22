package com.botwclimb.common.climb;

/** All tuning numbers live here. Distances are blocks, speeds are blocks/tick. */
public final class ClimbConstants {
    private ClimbConstants() {}

    // --- normal climbing ---
    public static final double CLIMB_SPEED = 0.11;
    /** Sideways climbing uses the same speed as up/down: one pace in every direction. */
    public static final double SIDE_SPEED = CLIMB_SPEED;
    /** Wanted distance between the player's wall-side face and the wall. */
    public static final double ADHESION_GAP = 0.02;
    public static final double ADHESION_MAX_PULL = 0.12;

    // --- surface sampling (offsets from the feet; the player is 1.8 tall) ---
    public static final double PROBE_DEPTH = 0.45;
    public static final double[][] BANDS = {{0.1, 0.6}, {0.7, 1.2}, {1.3, 1.7}}; // legs, torso, head
    public static final double ATTACH_GAP = 0.30;
    public static final double HOLD_GAP = 0.40;
    public static final double REGRAB_GAP = 0.45;
    /** Hold the climb key this long (ticks, 8 = 0.4 s) next to a wall to start climbing. */
    public static final int CLIMB_HOLD_TICKS = 4; // 0.2 s

    // --- climb jump ---
    public static final int JUMP_WINDUP_TICKS = 2;
    public static final int JUMP_COOLDOWN = 8;
    public static final int ATTACH_COOLDOWN = 8;
    public static final int JUMP_SEARCH_TICKS = 3;
    /** Vertical reach of one jump, and how far outward (from the flush position) it may swing to clear a lip. */
    // ---- stamina: three wheels of 750 units, 2250 at most; every extra wheel is five upgrades of 150
    public static final float STAMINA_WHEEL = 750f, STAMINA_PER_UPGRADE = 187.5f; // an upgrade adds a quarter of a wheel
    public static final int STAMINA_MAX_UPGRADES = 8;   // the first wheel is free, then 4 quarters for each of the two extra wheels
    public static final float STAMINA_CLIMB_DRAIN = 36.5f / 20f;   // per tick while moving on the wall (36.5 units/second)
    // jump costs, as a share of the first wheel (750): wall jump 22.5%, ledge jump 13%, long (2+ deep) ledge jump 17.5%
    public static final float STAMINA_JUMP_COST = 150f, STAMINA_LEDGE_COST = 97.5f, STAMINA_LONG_LEDGE_COST = 131.25f;
    public static final float STAMINA_REGEN = 345f / 20f;          // per tick when not climbing and on the ground
    public static final float STAMINA_FALL_NO_REGEN = 4f;          // no recovery once the fall is this many blocks long (an ordinary jump is about 1)
    public static final int STAMINA_REGEN_DELAY = 10, STAMINA_EXHAUST_DELAY = 30;
    public static final float STAMINA_ATTACH_MIN = 60f;            // need this much to grab a wall
    /** Climb Speed enchantment (boots): faster wall climbing, and faster side / forward / diagonal wall jumps (never the ledge jumps). */
    /** Playback speed of every climbing (wall) jump: 20% slower than the original planned pace. */
    public static final double JUMP_BASE_SPEED = 0.8;
    public static final double ENCHANT_CLIMB_MULT = 1.25, ENCHANT_JUMP_MULT = 1.15;
    public static final double JUMP_MAX_RISE = 3.5;
    /** Height of a jump up a plain wall (3.5 before, -20%). Clearing a lip may still use the full JUMP_MAX_RISE. */
    public static final double JUMP_OPEN_RISE = 2.8;
    public static final double JUMP_MAX_OUT = 1.5;
    /** Total path length budget (out + up + back). Keeps the jump from crawling across big geometry. */
    public static final double JUMP_MAX_PATH = 6.4;
    public static final double JUMP_MIN_GAIN = 0.75;
    /** Only wall faces this close to the original wall plane count as "the wall"; no clinging to a lip's outer face. */
    public static final double JUMP_MAX_WALL_OUT = 0.5;
    /** How far a jump drifts sideways when A / D is held. */
    public static final double JUMP_SIDE_REACH = 1.5;
    /** A / D alone: a low leap this far along the wall, arcing this high. */
    public static final double JUMP_SIDE_LEAP = 3.0;
    public static final double JUMP_SIDE_RISE = 0.15;
    public static final double JUMP_GRID = 0.25;
    public static final double JUMP_CLEARANCE = 0.04;
    public static final double JUMP_AVG_SPEED = 0.48;
    public static final int JUMP_MIN_TICKS = 6;
    public static final int JUMP_MAX_TICKS = 13;
    /** Without any climbable goal the jump is just a hop this high (still collision limited). */
    public static final double HOP_RISE = 1.6;
    public static final double HOP_OUT = 0.15;

    // --- ledge / corner ---
    public static final double LEDGE_RISE_SPEED = 0.16; // 20% lower than before
    public static final double LEDGE_FORWARD_SPEED = 0.16;
    public static final int LEDGE_MAX_TICKS = 100; // safety limit only: a long dash + rise + hang + the climb-up takes ~50 ticks, and the old 40 dropped you mid-climb
    public static final double CORNER_SPEED = 0.14;
    public static final int CORNER_MAX_TICKS = 10;

    /** Damage while on a wall: let go, fall and flail for this many ticks (20 = 1 s). */
    public static final int RAGDOLL_TICKS = 20;

    // --- server validation ---
    public static final int SERVER_GRACE_TICKS = 6;
    public static final double SERVER_EXTRA_GAP = 0.2;
}
