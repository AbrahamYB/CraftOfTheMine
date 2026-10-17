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
    public boolean jumpHeld, sneak;
    public boolean ignoreSneak; // a Shift held as part of the climb key combo is not a 'let go'
    public int keyHold; // ticks the climb key has been held next to a wall

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
    // pose inertialization (client render): see ClimbAnimator.inertia
    public final float[] poseOut = new float[12], poseOff = new float[12];
    public int poseAge = 999;
    public boolean poseEvent;                 // a pose state switch happened this tick: absorb it
    public ClimbState prevPoseState = ClimbState.NONE;
    public float ledgeU;                      // 0..1 progress of the ledge grip pose
    public double ledgeY0;
    public int ledgeFwd;
    public boolean pendingBlockJump;          // attached this tick by Space under a block: the block jump starts next tick
    public double ledgeBlockMin;              // underside of the block being jumped onto
    public boolean ledgeGrabbed;              // hands are on the block's edge
    public int mantleTick;                    // ticks into the climb-up onto the block (0 = not started)
    public float mantleT, mantleTO;           // 0..1 progress of the climb-up: movement and animation both read this
    public Vec3 mantleStart = Vec3.ZERO;
    public float ledgeGap, ledgeGapO;         // how far the shoulders are below the ledge top (render: where the hands must reach)
    public int ledgeSide;                     // W+A (1) / W+D (-1) into the block jump: it drifts that way; 0 = straight
    public double ledgeOutSpeed;
    public double ledgeLat, ledgeLatTotal;    // sideways travel so far / in total over the whole jump (1 block when the block is diagonally above)
    public int ledgeHang;                     // ticks left of hanging from the edge before the pull-up
    public double ledgeOutNeed, ledgeOut;     // the jump-onto-block arc: how far out it must go first
    public int latchTicks;                    // ticks left of the hop when first latching onto a wall
    public int ragdollTicks;                  // client: ticks left of the fall-and-flail after being hit on a wall (20 = 1 s)
    public int ragdollAge;
    public float phaseOffset;                 // shifts the climb cycle (kept for the pose; 0 unless changed)
    public int grabAge = 999;                 // ticks since this player latched onto a wall from the ground (999 = never)
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
