package com.botwclimb.common.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.ClimbState;
import com.botwclimb.network.ParagliderStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.botwclimb.common.paraglider.ParagliderConstants.*;

/** Paraglider movement (own state, never vanilla fall-flying) and server-side legality checks. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class ParagliderController {
    private ParagliderController() {}

    /** Conditions that must hold for as long as the glider is open. */
    public static boolean stateAllows(Player p) {
        ClimbData c = p.getData(BotwClimb.CLIMB);
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger() && !p.isSwimming()
                && !p.isUnderWater() && !p.isInWater() && !p.isInLava() && !p.isFallFlying()
                && !p.getAbilities().flying && c.state == ClimbState.NONE;
    }

    public static boolean canDeploy(Player p) {
        return stateAllows(p) && !p.onGround();
    }

    // ------------------------------------------------------------------ movement (controlling client)

    /**
     * One glide tick. Current velocity is the real displacement of the last tick (so walls and ceilings slow
     * us), horizontal velocity bends towards the camera-relative wish direction, vertical velocity settles on
     * the descent speed. Collision stays with vanilla move().
     */
    public static void step(Player p, ParagliderData g, float forward, float strafe) {
        Vec3 pos = p.position();
        Vec3 cur = g.fresh ? p.getDeltaMovement() : pos.subtract(g.prevPos);
        g.fresh = false;
        g.prevPos = pos;

        double yaw = Math.toRadians(p.getYRot());
        double wx = -Math.sin(yaw) * forward + Math.cos(yaw) * strafe;
        double wz = Math.cos(yaw) * forward + Math.sin(yaw) * strafe;
        double len = Math.hypot(wx, wz);
        boolean wish = len > 1.0E-3;
        if (len > 1) {
            wx /= len;
            wz /= len;
        }
        double tx = wish ? wx * MAX_HORIZONTAL_SPEED : 0, tz = wish ? wz * MAX_HORIZONTAL_SPEED : 0;

        double vx = cur.x, vz = cur.z;
        double ex = tx - vx, ez = tz - vz, dist = Math.hypot(ex, ez);
        double accel = wish ? HORIZONTAL_ACCELERATION : HORIZONTAL_DECELERATION;
        if (dist <= accel) {
            vx = tx;
            vz = tz;
        } else {
            vx += ex / dist * accel;
            vz += ez / dist * accel;
        }

        double vy = cur.y;
        vy = vy < DESCENT_SPEED ? Math.min(DESCENT_SPEED, vy + CATCH_RATE) : Math.max(DESCENT_SPEED, vy - SETTLE_RATE);

        p.setDeltaMovement(vx, vy, vz);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------------ server

    public static void broadcast(ServerPlayer p, boolean gliding, boolean correction) {
        var payload = new ParagliderStatePayload(p.getId(), gliding, correction);
        if (correction) PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, payload);
        else PacketDistributor.sendToPlayersTrackingEntity(p, payload);
    }

    /** Handles a client toggle request. Closing is always fine; opening must pass the same checks as the client. */
    public static void serverToggle(ServerPlayer p, boolean open) {
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        if (open == g.gliding) return;
        // fallDistance > 0: the player just left the ground and the position packet saying so is still on its way
        if (open && !(canDeploy(p) || (stateAllows(p) && p.fallDistance > 0))) {
            broadcast(p, false, true);
            return;
        }
        g.gliding = open;
        g.groundTicks = 0;
        p.fallDistance = 0;
        broadcast(p, open, false);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        if (!g.gliding) return;
        g.groundTicks = p.onGround() ? g.groundTicks + 1 : 0;
        if (!stateAllows(p) || g.groundTicks >= SERVER_GROUND_GRACE) {
            g.gliding = false;
            broadcast(p, false, true);
        } else {
            p.fallDistance = 0;
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target
                && target.getData(BotwClimb.PARAGLIDER).gliding) {
            PacketDistributor.sendToPlayer(viewer, new ParagliderStatePayload(target.getId(), true, false));
        }
    }
}
