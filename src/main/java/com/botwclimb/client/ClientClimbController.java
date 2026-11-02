package com.botwclimb.client;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbConstants;
import com.botwclimb.client.paraglider.ClientParagliderController;
import com.botwclimb.common.climb.ClimbController;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.ClimbState;
import com.botwclimb.common.climb.SurfaceContact;
import com.botwclimb.common.climb.SurfaceSampler;
import com.botwclimb.network.ClimbInputPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
//? if <1.21.2
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

/**
 * Client glue: reads input and drives the local climb simulation, tells the server what it is doing, keeps the
 * animation numbers of every visible player up to date, and orients/leans the rendered body.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class ClientClimbController {
    /** How long turning round a corner to face the next wall takes (0.45 s). */
    private static final int TURN_TICKS = 9;
    private static boolean pushed;

    private ClientClimbController() {}

    // ------------------------------------------------------------------ local control

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer p)) return;
        var in = event.getInput();
        ClimbData d = p.getData(BotwClimb.CLIMB);
        float rawForward = ClientCompat.forward(in), rawStrafe = ClientCompat.strafe(in);
        // NeoForge hands a key press only to the bindings that match the held modifiers when there are any: Shift + Space goes to the climb and
        // glider bindings and vanilla's jump never sees it (so a jump with Shift held, bridging and pillaring, did nothing). Give it back here.
        var jumpKey = Minecraft.getInstance().options.keyJump.getKey();
        if (!ClientCompat.jumping(in) && (ClimbKeys.CLIMB.isDown() && ClimbKeys.CLIMB.getKey().equals(jumpKey)
                || com.botwclimb.client.paraglider.ParagliderKeys.PARAGLIDE.isDown() && com.botwclimb.client.paraglider.ParagliderKeys.PARAGLIDE.getKey().equals(jumpKey))) {
            ClientCompat.jumping(in, true);
        }
        ClientParagliderController.handleChord(p, in); // must run before anything reads in.jumping

        // Shift that is part of the climb key combo must not count as "let go" while grabbing the wall,
        // nor afterwards until it has been released once. Sprint is not used at all.
        KeyModifier mod = ClimbKeys.CLIMB.getKeyModifier();
        boolean sneak = ClientCompat.shift(in);
        boolean comboShift = mod == KeyModifier.SHIFT;
        if (d.ignoreSneak && !sneak) d.ignoreSneak = false;
        if (comboShift && (d.ignoreSneak || !d.active())) sneak = false;

        if (d.ragdollTicks > 0) { // knocked off: no control until the fall is over
            ClientCompat.stopMoving(in);
            ClientCompat.clearDirections(in);
            ClientCompat.jumping(in, false);
            ClientCompat.shift(in, false);
            return;
        }
        boolean climbKey = ClimbKeys.CLIMB.isDown();
        boolean jump = ClientCompat.jumping(in);
        if (!d.active() && climbKey && ClimbKeys.CLIMB.getKey().equals(Minecraft.getInstance().options.keyJump.getKey())
                && ClimbController.findWall(p) != null) { // only next to a wall it can grab: in the open Shift + Space is an ordinary jump (bridging, pillaring)
            ClientCompat.jumping(in, false); // Space is part of the combo: no vanilla hop while holding it
            // `jump` stays true on purpose: the climb tick must see Space as already held, otherwise the moment the
            // grab succeeds the held Space looks like a brand new press and fires a jump straight away
        }
        boolean wasActive = d.active();
        ClimbController.stepLocal(p, d, ClientCompat.forward(in), ClientCompat.strafe(in), jump, climbKey, sneak);
        if (!wasActive && d.active()) {
            d.ignoreSneak = comboShift && ClientCompat.shift(in);
        }

        ClientParagliderController.steer(p, in, rawForward, rawStrafe, d.active());
        if (d.active()) { // the climb simulation owns movement now: swallow vanilla walking, jumping and crouching
            ClientCompat.stopMoving(in);
            ClientCompat.clearDirections(in);
            ClientCompat.jumping(in, false);
            ClientCompat.shift(in, false);
        }
        if (d.state != d.sentState || (d.active() && d.dir != d.sentDir)) {
            d.sentState = d.state;
            d.sentDir = d.dir;
            com.botwclimb.client.ClientCompat.sendToServer(new ClimbInputPayload(d.state, d.dir));
        }
    }

    // ------------------------------------------------------------------ animation bookkeeping (all players)

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide() || !(p instanceof AbstractClientPlayer)) return;
        if (!p.hasData(BotwClimb.CLIMB)) return;
        animTick(p, p.getData(BotwClimb.CLIMB));
    }

    private static void animTick(Player p, ClimbData d) {
        Vec3 pos = p.position();
        if (!d.animInit) {
            d.animInit = true;
            d.lastPos = pos;
        }
        d.blendO = d.blend;
        d.leanO = d.lean;
        d.bodyYawO = d.bodyYaw;
        d.distUpO = d.distUp;
        d.distSideO = d.distSide;
        d.speedUpPrev = d.speedUp;
        d.speedSidePrev = d.speedSide;
        d.jumpAgeO = d.jumpAge;

        Vec3 delta = pos.subtract(d.lastPos);
        d.lastPos = pos;

        if (d.state == ClimbState.CLIMB_JUMP && d.animPrev != ClimbState.CLIMB_JUMP) {
            d.jumpAge = 0;
            d.jumpAgeO = 0;
            d.jumpSide = -d.jumpSide;
            d.jumpEndAge = 999;
        } else if (d.animPrev == ClimbState.CLIMB_JUMP && d.state != ClimbState.CLIMB_JUMP) {
            d.jumpEndAge = 0;
        }
        if (d.animPrev == ClimbState.NONE && d.state == ClimbState.CLIMBING) d.grabAge = 0; // fresh latch: play the hook
        if (d.state != d.prevPoseState) d.poseEvent = true; // a state switch is inertialized
        d.prevPoseState = d.state;
        d.animPrev = d.state;
        if (d.grabAge < 999) d.grabAge++;
        if (d.ragdollTicks > 0) { d.ragdollTicks--; d.ragdollAge++; }
        if (d.jumpAge < 999) d.jumpAge++;
        if (d.jumpEndAge < 999 && d.state != ClimbState.CLIMB_JUMP) d.jumpEndAge++;

        if (d.active()) {
            Direction dir = d.dir;
            float targetYaw = dir.toYRot();
            if (d.blend <= 0.001f) {
                d.bodyYaw = targetYaw;
                d.bodyYawO = targetYaw;
                d.turnTo = targetYaw;
                d.turnAge = 999;
                d.lean = d.leanO = 0;
            } else {
                if (Math.abs(Mth.wrapDegrees(targetYaw - d.turnTo)) > 1f) { // a new wall to face (a corner, inside or out): turn to it over a few ticks
                    d.turnFrom = d.bodyYaw;
                    d.turnTo = targetYaw;
                    d.turnAge = 0;
                }
                if (d.turnAge < TURN_TICKS) {
                    float u = ++d.turnAge / (float) TURN_TICKS;
                    u = u * u * u * (u * (u * 6f - 15f) + 10f); // a gentle start and finish
                    d.bodyYaw = d.turnFrom + Mth.wrapDegrees(d.turnTo - d.turnFrom) * u;
                } else {
                    d.bodyYaw = d.turnTo;
                }
            }
            // the limb cycle and the lean follow the way the body is actually facing while it turns round a corner, not the wall it is about to face,
            // so nothing in the pose jumps when the direction changes (other players see this too)
            double yawR = Math.toRadians(d.bodyYaw), fx = -Math.sin(yawR), fz = Math.cos(yawR);
            boolean tracked = d.state != ClimbState.CLIMB_JUMP; // the jump has its own pose
            double up = tracked ? delta.y : 0;
            double side = tracked ? delta.x * fz - delta.z * fx : 0; // left of the facing direction
            // the phase only advances with real surface movement: blocked = no limb cycling
            d.distUp += up;
            d.distSide += side;
            d.speedUp += (up - d.speedUp) * 0.5;
            d.speedSide += (side - d.speedSide) * 0.3;
            d.blend = Math.min(1f, d.blend + 0.34f);

            AABB box = p.getBoundingBox();
            if (d.state == ClimbState.CLIMBING) { // remember where the wall plane is
                SurfaceContact c = SurfaceSampler.sample(p.level(), p, box, dir);
                if (!Double.isNaN(c.gap()) && c.any()) d.planeCoord = faceCoord(box, dir) + step(dir) * c.gap();
            }
            double lean = 0;
            if (!Double.isNaN(d.planeCoord)) {
                lean = step(dir) * (d.planeCoord - faceCoord(box, dir)) - ClimbConstants.ADHESION_GAP;
            }
            // the lean eases in and out; while on a shelf the pose itself handles the lean, so the model is not rotated
            double target = Mth.clamp(lean, 0, 2);
            d.lean += (float) (target - d.lean) * 0.2f;
        } else {
            d.blend = Math.max(0f, d.blend - 0.5f); // letting go (crouch) drops the pose almost at once
            d.lean *= 0.6f;
            d.speedUp *= 0.5;
            d.speedSide *= 0.5;
            d.planeCoord = Double.NaN;
        }
    }

    private static int step(Direction dir) {
        return dir.getAxisDirection().getStep();
    }

    private static double faceCoord(AABB box, Direction dir) {
        boolean pos = step(dir) > 0;
        return switch (dir.getAxis()) {
            case X -> pos ? box.maxX : box.minX;
            case Z -> pos ? box.maxZ : box.minZ;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ rendering

    /** Turns the body to face the wall (head and camera stay free), snuggles it up to the wall and leans it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre event) {
        if (!(RenderData.entity(event) instanceof AbstractClientPlayer p) || !p.hasData(BotwClimb.CLIMB)) return;
        ClimbData d = p.getData(BotwClimb.CLIMB);
        float pt = event.getPartialTick();
        float w = ClimbAnimator.blend(d, pt);
        if (w <= 0.001f) return;

        // render-only: the logical yaw is never touched, only the cosmetic body yaw
        float vanilla = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float yaw = Mth.rotLerp(w, vanilla, Mth.rotLerp(pt, d.bodyYawO, d.bodyYaw));
        RenderData.setBodyYaw(event, p, yaw);

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        pushed = true;
        float facing = (float) Math.toRadians(Mth.rotLerp(pt, d.bodyYawO, d.bodyYaw)); // the body's own turning facing: nothing here jumps at a corner
        float dx = -Mth.sin(facing), dz = Mth.cos(facing);
        float snug = 0.10f * w;
        ps.translate(dx * snug, 0, dz * snug);

        float lean = (ClimbAnimator.leanDegrees(d, pt) + ClimbAnimator.leapDegrees(d, pt)) * w;
        if (lean > 0.01f) {
            ps.rotateAround(new Quaternionf().rotationAxis(lean * (float) (Math.PI / 180), -dz, 0, dx), // the clockwise side of the facing
                    0, 0.9f, 0);
        }
        float roll = ClimbAnimator.rollDegrees(d, pt) * w; // leaning into the direction of travel / jump swing
        if (Math.abs(roll) > 0.01f) {
            ps.rotateAround(new Quaternionf().rotationAxis(-roll * (float) (Math.PI / 180), dx, 0, dz),
                    0, 0.9f, 0);
        }
        float stretch = ClimbAnimator.stretch(d, pt);
        if (stretch != 1f) ps.scale(1f, stretch, 1f);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(RenderLivingEvent.Post event) {
        if (pushed) {
            event.getPoseStack().popPose();
            pushed = false;
        }
    }
}
