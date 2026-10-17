package com.botwclimb.common.paraglider;

import net.minecraft.world.phys.Vec3;

/** Per-player paraglider state (non-persistent attachment; client and server keep separate copies). */
public final class ParagliderData {
    public boolean gliding;
    public int rocketTier = 1;        // flight duration (1..3) of the last rocket used while Elytra-flying
    public int boostAge, boostDuration; // ticks since the glider opened / how long the carried momentum lasts
    public double boostSpeed;          // horizontal speed carried over when the glider opened
    public int fabric;               // index into FabricRegistry (not saved: back to ordinary after a relog)
    public int groundTicks;          // server: consecutive ticks on the ground while gliding
    public boolean fresh;            // client: just opened, take the starting velocity from the player
    public Vec3 prevPos = Vec3.ZERO; // client: position at the previous glide step

    // ---- animation (client, all players) ----
    public float deploy, deployO;          // 0 closed .. 1 fully open
    public float heading, headingO;        // degrees, direction of travel
    public float bank, bankO;              // degrees, + = leaning to the player's right
    public float pitch, pitchO;            // degrees, + = tilted forward
    public float lagF, lagFO, lagR, lagRO; // body swing from acceleration (forward / right)
    public float windF, windFO, windR, windRO; // air rushing past the canopy, forward / right (-1..1)
    public float gust;                         // extra flutter from falling air and rocket boosts (0..1)
    public Vec3 lastPos = Vec3.ZERO;
    public Vec3 lastVel = Vec3.ZERO;
    public boolean animInit;

    public boolean visible() {
        return deploy > 0f || deployO > 0f;
    }
}
