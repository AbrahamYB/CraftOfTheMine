package com.botwclimb.common.climb;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player climbing state (a non-persistent data attachment, so client and server each get their own copy).
 * Public mutable fields on purpose: this is a small state bag, not an API.
 */
public final class ClimbData {
    // ---- shared state ----
    public ClimbState state = ClimbState.NONE;
    public Direction dir = Direction.NORTH; // horizontal direction from the player towards the wall
    public int stateTicks;                  // ticks since the last state change
    public int attachCooldown;
    public int jumpCooldown;

    // ---- local (controlling client) input ----
    public float forward, strafe;
    public boolean jumpHeld, sneak, sprint;
    public boolean ignoreSneak, ignoreSprint; // a Shift/Ctrl held as part of the climb key combo is not a 'let go'
    public int keyHold; // ticks the climb key has been held next to a wall
    public int lastForwardPressTick = -100;
    public boolean forwardWasDown;

    // ---- jump / ledge / corner execution ----
    public JumpPlan plan;
    public Vec3 jumpStart = Vec3.ZERO;
    public int jumpTick;
    public int ledgeTicks;
    public double ledgeY;
    public int cornerTicks;
    public Direction cornerTo;

    // ---- server bookkeeping ----
    public ClimbState claim = ClimbState.NONE;
    public Direction claimDir = Direction.NORTH;
    public int badTicks;
    public int grace;
    public Vec3 lastValidPos = Vec3.ZERO;
    public boolean claimJumpPending;
    public boolean warned;

    // ---- animation (client, all players) ----
    public double planeCoord = Double.NaN;  // world coordinate of the wall plane along the wall axis
    public double distUp, distUpO, distSide, distSideO; // accumulated actual surface travel, signed (side: + is left)
    public double speedUp, speedUpPrev, speedSide, speedSidePrev; // smoothed per-tick speeds
    public ClimbState animPrev = ClimbState.NONE;
    public ClimbState sentState = ClimbState.NONE;   // last claim sent to the server (controlling client)
    public Direction sentDir = Direction.NORTH;
    public float blend, blendO;             // 0..1 how much the climb pose is applied
    public float lean, leanO;               // outward excursion from the wall, blocks
    public float bodyYaw, bodyYawO;
    public int jumpAge = 999, jumpAgeO = 999; // ticks since this jump started (keeps counting after regrab)
    public int jumpEndAge = 999;              // ticks since the jump ended (999 = not recently)
    public Vec3 lastPos = Vec3.ZERO;
    public boolean animInit;
    public int jumpSide = 1;                  // alternates every jump: which way the body swings

    public void setState(ClimbState s) {
        if (s != state) {
            StackWalker.getInstance().walk(f -> f.skip(1).limit(2).map(x -> x.getMethodName() + ":" + x.getLineNumber()).toList())
                    .forEach(w -> com.botwclimb.BotwClimb.LOGGER.info("[{}] {} -> {} via {}", Thread.currentThread().getName(), state, s, w));
            state = s;
            stateTicks = 0;
        }
    }

    public boolean active() {
        return state != ClimbState.NONE;
    }
}
