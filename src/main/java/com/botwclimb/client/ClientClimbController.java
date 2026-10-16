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
    private static boolean pushed;

    private ClientClimbController() {}

    // ------------------------------------------------------------------ local control

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer p)) return;
        Input in = event.getInput();
        ClimbData d = p.getData(BotwClimb.CLIMB);
        float rawForward = in.forwardImpulse, rawStrafe = in.leftImpulse;
        ClientParagliderController.handleChord(p, in); // must run before anything reads in.jumping

        // Sprinting (key held, or the double-tap-forward gesture) lets go of the wall, just like crouching does.
        boolean sprint = Minecraft.getInstance().options.keySprint.isDown();
        boolean tap = in.up && !d.forwardWasDown && p.tickCount - d.lastForwardPressTick <= 7;
        if (in.up && !d.forwardWasDown) d.lastForwardPressTick = p.tickCount;
        d.forwardWasDown = in.up;
        sprint |= tap && d.active();

        // Shift / Ctrl that are part of the climb key combo must not count as "let go" while grabbing the wall,
        // nor afterwards until they have been released once.
        KeyModifier mod = ClimbKeys.CLIMB.getKeyModifier();
        boolean sneak = in.shiftKeyDown;
        boolean comboShift = mod == KeyModifier.SHIFT, comboCtrl = mod == KeyModifier.CONTROL;
        if (d.ignoreSneak && !sneak) d.ignoreSneak = false;
        if (d.ignoreSprint && !sprint) d.ignoreSprint = false;
        if (comboShift && (d.ignoreSneak || !d.active())) sneak = false;
        if (comboCtrl && (d.ignoreSprint || !d.active())) sprint = false;

        boolean climbKey = ClimbKeys.CLIMB.isDown();
        boolean jump = in.jumping;
        if (!d.active() && climbKey && ClimbKeys.CLIMB.getKey().equals(Minecraft.getInstance().options.keyJump.getKey())) {
            in.jumping = false; // Space is part of the combo: no hopping while holding it
            jump = false;
        }
        boolean wasActive = d.active();
        ClimbController.stepLocal(p, d, in.forwardImpulse, in.leftImpulse, jump, climbKey, sneak, sprint);
        if (!wasActive && d.active()) {
            d.ignoreSneak = comboShift && in.shiftKeyDown;
            d.ignoreSprint = comboCtrl && Minecraft.getInstance().options.keySprint.isDown();
        }

        ClientParagliderController.steer(p, in, rawForward, rawStrafe, d.active());
        if (d.active()) { // the climb simulation owns movement now: swallow vanilla walking, jumping and crouching
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.up = in.down = in.left = in.right = false;
            in.jumping = false;
            in.shiftKeyDown = false;
        }
        if (d.state != d.sentState || (d.active() && d.dir != d.sentDir)) {
            d.sentState = d.state;
            d.sentDir = d.dir;
            PacketDistributor.sendToServer(new ClimbInputPayload(d.state, d.dir));
        }
    }

    // ------------------------------------------------------------------ animation bookkeeping (all players)

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide || !(p instanceof AbstractClientPlayer)) return;
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
        d.animPrev = d.state;
        if (d.jumpAge < 999) d.jumpAge++;
        if (d.jumpEndAge < 999 && d.state != ClimbState.CLIMB_JUMP) d.jumpEndAge++;

        if (d.active()) {
            Direction dir = d.dir;
            Direction left = dir.getCounterClockWise();
            boolean tracked = d.state != ClimbState.CLIMB_JUMP; // the jump has its own pose
            double up = tracked ? delta.y : 0;
            double side = tracked ? delta.x * left.getStepX() + delta.z * left.getStepZ() : 0;
            // the phase only advances with real surface movement: blocked = no limb cycling
            d.distUp += up;
            d.distSide += side;
            d.speedUp += (up - d.speedUp) * 0.5;
            d.speedSide += (side - d.speedSide) * 0.3;

            float targetYaw = dir.toYRot();
            if (d.blend <= 0.001f) {
                d.bodyYaw = targetYaw;
                d.bodyYawO = targetYaw;
                d.lean = d.leanO = 0;
            } else {
                d.bodyYaw += Mth.wrapDegrees(targetYaw - d.bodyYaw) * 0.45f;
            }
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
            d.lean = (float) Mth.clamp(lean, 0, 2);
        } else {
            d.blend = Math.max(0f, d.blend - 0.5f); // letting go (crouch/sprint) drops the pose almost at once
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
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p) || !p.hasData(BotwClimb.CLIMB)) return;
        ClimbData d = p.getData(BotwClimb.CLIMB);
        float pt = event.getPartialTick();
        float w = ClimbAnimator.blend(d, pt);
        if (w <= 0.001f) return;

        // render-only: the logical yaw is never touched, only the cosmetic body yaw
        float vanilla = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float yaw = Mth.rotLerp(w, vanilla, Mth.rotLerp(pt, d.bodyYawO, d.bodyYaw));
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        pushed = true;
        Direction dir = d.dir;
        float snug = 0.10f * w;
        ps.translate(dir.getStepX() * snug, 0, dir.getStepZ() * snug);

        float lean = ClimbAnimator.leanDegrees(d, pt) * w;
        if (lean > 0.01f) {
            Direction t = dir.getClockWise();
            ps.rotateAround(new Quaternionf().rotationAxis(lean * (float) (Math.PI / 180), t.getStepX(), 0, t.getStepZ()),
                    0, 0.9f, 0);
        }
        float roll = ClimbAnimator.rollDegrees(d, pt) * w; // leaning into the direction of travel / jump swing
        if (Math.abs(roll) > 0.01f) {
            ps.rotateAround(new Quaternionf().rotationAxis(-roll * (float) (Math.PI / 180), dir.getStepX(), 0, dir.getStepZ()),
                    0, 0.9f, 0);
        }
        float stretch = ClimbAnimator.stretch(d, pt);
        if (stretch != 1f) ps.scale(1f, stretch, 1f);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (pushed) {
            event.getPoseStack().popPose();
            pushed = false;
        }
    }
}
