package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbController;
import com.botwclimb.common.paraglider.InputChord;
import com.botwclimb.common.paraglider.ParagliderConstants;
import com.botwclimb.common.paraglider.ParagliderController;
import com.botwclimb.common.paraglider.ParagliderData;
import com.botwclimb.network.ParagliderTogglePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.core.component.DataComponents;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

/**
 * Client glue for the paraglider: handles the toggle chord and its Space consumption inside
 * MovementInputUpdateEvent, steers the glide, tracks animation numbers for every visible player and renders the
 * bank / pitch of the whole rig.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class ClientParagliderController {
    private static boolean pushed;

    private ClientParagliderController() {}

    // ------------------------------------------------------------------ input (called from the climb input hook)

    /**
     * Runs right after vanilla has read the keys and BEFORE LocalPlayer reaches its jump / Elytra handling (that
     * code reads {@code input.jumping} afterwards), so clearing {@code jumping} here keeps this Space press away
     * from vanilla. Must run before the climb code reads the jump flag.
     */
    public static void handleChord(LocalPlayer p, Input in) {
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        InputChord chord = ChordInputHandler.PARAGLIDE;
        chord.clearIfKeyUp(ChordInputHandler.keyPhysicallyDown());
        if (chord.takePending()) {
            if (ParagliderController.canSwitchFromElytra(p)) {
                // flying on an Elytra (rockets or not): switch to the glider on the spot, keeping the momentum
                p.stopFallFlying();
                setGliding(g, true);
                g.fresh = true;
                chord.consume();
            } else if (g.gliding && canResumeElytra(p)) {
                // gliding with an Elytra equipped: Shift + Space hands the flight back to the Elytra, momentum intact
                setGliding(g, false);
                p.startFallFlying();
                p.connection.send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                chord.consume();
            } else if (g.gliding || canOpen(p)) {
                chord.begin(); // swallow this Space press at once (no jump, no Elytra) and start the 0.4 s hold
            }
        }
        if (chord.holding()) {
            boolean held = ChordInputHandler.keyPhysicallyDown()
                    && ParagliderKeys.PARAGLIDE.getKeyModifier().isActive(KeyConflictContext.IN_GAME);
            if (chord.tickHold(held, ParagliderConstants.HOLD_TICKS)) {
                if (g.gliding) {
                    setGliding(g, false);
                } else if (canOpen(p)) {
                    setGliding(g, true);
                    g.fresh = true;
                }
            }
        }
        // a plain Space press after falling off a ledge opens the glider instead of vanilla's Elytra start
        if (in.jumping && !g.gliding && p.fallDistance >= ParagliderConstants.OPEN_AFTER_FALL
                && !p.isFallFlying() && canOpen(p)) {
            setGliding(g, true);
            g.fresh = true;
            in.jumping = false;
        }
        if (chord.consumed() && ParagliderKeys.PARAGLIDE.getKey().equals(Minecraft.getInstance().options.keyJump.getKey())) {
            in.jumping = false; // consumed press: stays false until the key is released
        }
    }

    /** The rocket used while Elytra-flying sets how long its momentum lasts (the most recent one counts). */
    @SubscribeEvent
    public static void onRocket(PlayerInteractEvent.RightClickItem event) {
        var rocket = event.getItemStack().get(DataComponents.FIREWORKS);
        if (rocket == null || !event.getEntity().isFallFlying()) return;
        event.getEntity().getData(BotwClimb.PARAGLIDER).rocketTier = rocket.flightDuration();
    }

    private static boolean canResumeElytra(LocalPlayer p) {
        return !p.onGround() && !p.isInWater() && p.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(p);
    }

    private static boolean canOpen(LocalPlayer p) {
        // next to a climbable wall the same chord means "climb" instead
        return ParagliderController.canDeploy(p) && ClimbController.findWall(p) == null;
    }

    private static void setGliding(ParagliderData g, boolean open) {
        g.gliding = open;
        PacketDistributor.sendToServer(new ParagliderTogglePayload(open));
    }

    /** Runs after the climb code. Moves the glider and takes over movement input while it is open. */
    public static void steer(LocalPlayer p, Input in, float forward, float strafe, boolean climbing) {
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        if (!g.gliding) return;
        if (climbing || !ParagliderController.stateAllows(p) || p.onGround()) {
            setGliding(g, false); // climbing wins, Elytra / water / spectator end it, landing ends it at once
            return;
        }
        ParagliderController.step(p, g, forward, strafe);
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.shiftKeyDown = false; // a held Shift must not crouch the glider pilot
    }

    // ------------------------------------------------------------------ animation numbers (all players)

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (!p.level().isClientSide || !(p instanceof AbstractClientPlayer) || !p.hasData(BotwClimb.PARAGLIDER)) return;
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        Vec3 pos = p.position();
        if (!g.animInit) {
            g.animInit = true;
            g.lastPos = pos;
        }
        g.deployO = g.deploy;
        g.headingO = g.heading;
        g.bankO = g.bank;
        g.pitchO = g.pitch;
        g.lagFO = g.lagF;
        g.lagRO = g.lagR;
        g.rocketGustO = g.rocketGust;
        g.windFO = g.windF;
        g.windRO = g.windR;

        Vec3 vel = pos.subtract(g.lastPos);
        Vec3 acc = vel.subtract(g.lastVel);
        g.lastPos = pos;
        g.lastVel = vel;

        boolean wasShut = g.deploy <= 0f;
        g.deploy = g.gliding ? Math.min(1f, g.deploy + 1f / ParagliderConstants.DEPLOY_DURATION_TICKS)
                : Math.max(0f, g.deploy - 1f / ParagliderConstants.CLOSE_DURATION_TICKS);
        if (g.deploy <= 0f) {
            g.bank = g.pitch = g.lagF = g.lagR = g.windF = g.windR = 0;
            return;
        }
        double hs = Math.hypot(vel.x, vel.z);
        if (wasShut) {
            g.heading = g.headingO = hs > 0.03 ? (float) (Math.atan2(-vel.x, vel.z) * Mth.RAD_TO_DEG) : p.yBodyRot;
        } else if (hs > 0.03) { // tiny speeds keep the last meaningful heading
            float target = (float) (Math.atan2(-vel.x, vel.z) * Mth.RAD_TO_DEG);
            g.heading += Mth.wrapDegrees(target - g.heading) * 0.18f;
        }
        float turn = Mth.wrapDegrees(g.heading - g.headingO); // degrees per tick, + = turning right
        float max = ParagliderConstants.MAX_VISUAL_BANK_DEGREES;
        g.bank += (Mth.clamp(turn * 1.6f, -max, max) - g.bank) * 0.2f;
        float pitchTarget = (float) (hs / ParagliderConstants.MAX_HORIZONTAL_SPEED) * ParagliderConstants.MAX_VISUAL_PITCH_DEGREES;
        g.pitch += (Mth.clamp(pitchTarget, 0f, ParagliderConstants.MAX_VISUAL_PITCH_DEGREES) - g.pitch) * 0.15f;

        double h = Math.toRadians(g.heading);
        double fx = -Math.sin(h), fz = Math.cos(h);          // forward
        double accF = acc.x * fx + acc.z * fz;
        double accR = acc.x * -fz + acc.z * fx;               // right = forward rotated clockwise
        g.lagF += (Mth.clamp((float) (accF * 12), -1f, 1f) - g.lagF) * 0.2f;
        g.lagR += (Mth.clamp((float) (accR * 12), -1f, 1f) - g.lagR) * 0.2f;
        // the air moves against the glider: wind = -velocity, scaled so cruise is about 1
        double windF = -(vel.x * fx + vel.z * fz) / ParagliderConstants.MAX_HORIZONTAL_SPEED;
        double windR = -(vel.x * -fz + vel.z * fx) / ParagliderConstants.MAX_HORIZONTAL_SPEED;
        g.windF += (Mth.clamp((float) windF, -1f, 1f) - g.windF) * 0.15f;
        g.windR += (Mth.clamp((float) windR, -1f, 1f) - g.windR) * 0.15f;
        // falling air rushes up through the canopy, and a rocket boost adds more
        boolean rocketBoost = g.boostAge < g.boostDuration && g.boostSpeed > ParagliderConstants.MAX_HORIZONTAL_SPEED;
        float gustTarget = (float) Math.max(0, Math.min(1, -vel.y / 0.3)) * 0.5f + (rocketBoost ? 0.6f : 0f);
        g.gust += (Math.min(1f, gustTarget) - g.gust) * 0.1f;
        // follows the actual speed above cruise, so it calms as the carried momentum bleeds off
        g.rocketGust += (Mth.clamp((float) ((hs - 0.4) / 0.5), 0f, 1f) - g.rocketGust) * 0.06f;
    }

    // ------------------------------------------------------------------ rendering

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p) || !p.hasData(BotwClimb.PARAGLIDER)) return;
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        float pt = event.getPartialTick();
        float open = ParagliderAnimator.open(g, pt);
        if (open <= 0.01f) return;

        // render-only: the body turns to the direction of travel, the camera and logical yaw stay free
        float heading = Mth.rotLerp(pt, g.headingO, g.heading);
        float yaw = Mth.rotLerp(open, Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot), heading);
        p.yBodyRot = yaw;
        p.yBodyRotO = yaw;

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        pushed = true;
        double h = Math.toRadians(heading);
        float fx = (float) -Math.sin(h), fz = (float) Math.cos(h);
        float roll = ParagliderAnimator.rollDegrees(g, pt, p.tickCount + pt) * open;
        float pitch = ParagliderAnimator.pitchDegrees(g, pt) * open;
        // pivot at the hands: the whole rig banks and tilts about the grip
        ps.rotateAround(new Quaternionf().rotationAxis(roll * Mth.DEG_TO_RAD, fx, 0, fz), 0, 1.9f, 0);
        ps.rotateAround(new Quaternionf().rotationAxis(pitch * Mth.DEG_TO_RAD, fz, 0, -fx), 0, 1.9f, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (pushed) {
            event.getPoseStack().popPose();
            pushed = false;
        }
    }
}
