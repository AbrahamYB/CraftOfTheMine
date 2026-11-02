package com.botwclimb.common.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.ClimbState;
import com.botwclimb.network.ParagliderStatePayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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

    private static final ResourceLocation SLOW_FALL = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "slow_fall");

    /** Gravity is vanilla now; earlier versions saved a permanent slow-fall modifier on every player, so take it off. */
    @SubscribeEvent
    public static void onFallSpeed(PlayerTickEvent.Post event) {
        var gravity = event.getEntity().getAttribute(Attributes.GRAVITY);
        if (gravity != null && gravity.hasModifier(SLOW_FALL)) gravity.removeModifier(SLOW_FALL);
    }

    /** Conditions that must hold for as long as the glider is open. */
    public static boolean stateAllows(Player p) {
        return !p.isFallFlying() && baseAllows(p);
    }

    private static boolean baseAllows(Player p) {
        ClimbData c = p.getData(BotwClimb.CLIMB);
        return p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger() && !p.isSwimming()
                && !p.isUnderWater() && !p.isInWater() && !p.isInLava()
                && !p.getAbilities().flying && c.state == ClimbState.NONE && !c.exhausted;
    }

    /** Shift + Space while Elytra-flying (rocket boost or not) swaps straight over to the paraglider. */
    public static boolean canSwitchFromElytra(Player p) {
        return p.isFallFlying() && !p.onGround() && baseAllows(p);
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
        if (g.fresh) { // just opened: carry the Elytra speed; the rocket tier decides how long it lasts
            g.boostAge = 0;
            g.boostSpeed = Math.min(Math.hypot(cur.x, cur.z), BOOST_SPEED_CAP);
            g.boostDuration = ROCKET_MOMENTUM_TICKS[Math.max(1, Math.min(3, g.rocketTier)) - 1];
        }
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
        // the speed allowed right now: the carried-over speed eases down to cruise over the retention time (smoothstep)
        double allowance = MAX_HORIZONTAL_SPEED;
        boolean boosting = g.boostAge < g.boostDuration;
        if (boosting) {
            double t = Math.max(0, Math.min(1, g.boostAge / (double) g.boostDuration));
            allowance = Math.max(MAX_HORIZONTAL_SPEED,
                    g.boostSpeed + (MAX_HORIZONTAL_SPEED - g.boostSpeed) * (t * t * (3 - 2 * t)));
            g.boostAge++;
        }
        // steering aims at the allowance but never above the speed you already have (lost speed is not restored),
        // except up to cruise, which is ordinary paraglider acceleration
        double hs = Math.hypot(cur.x, cur.z);
        double target = Math.min(allowance, Math.max(hs, MAX_HORIZONTAL_SPEED));
        // while the boost lasts, letting go coasts on the same heading; afterwards it bleeds to a stop as before
        boolean coast = boosting && g.boostSpeed > MAX_HORIZONTAL_SPEED;
        double dirX = wish ? wx : (coast && hs > 1.0E-6 ? cur.x / hs : 0);
        double dirZ = wish ? wz : (coast && hs > 1.0E-6 ? cur.z / hs : 0);
        double tx = dirX * target, tz = dirZ * target;

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

        // never above the allowance; speed lost to collisions or turns is not put back
        double sp = Math.hypot(vx, vz);
        if (sp > allowance) {
            vx *= allowance / sp;
            vz *= allowance / sp;
        }

        double vy = cur.y;
        vy = vy < DESCENT_SPEED ? Math.min(DESCENT_SPEED, vy + CATCH_RATE) : Math.max(DESCENT_SPEED, vy - SETTLE_RATE);

        p.setDeltaMovement(vx, vy, vz);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------------ server

    public static void broadcast(ServerPlayer p, boolean gliding, boolean correction) {
        var payload = new ParagliderStatePayload(p.getId(), gliding, correction, p.getData(BotwClimb.PARAGLIDER).fabric);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, payload);
    }

    /** Stores the player's own fabric choice (not tied to any item) and tells everyone who can see them. */
    public static void setFabric(ServerPlayer p, int index) {
        if (index < 0 || index >= FabricRegistry.all().size() || !FabricUnlocks.has(p, index)) return; // only a fabric the player has learned
        forceFabric(p, index);
    }

    /** Wears a fabric whether it is learned or not (commands, and falling back to the ordinary one). */
    public static void forceFabric(ServerPlayer p, int index) {
        if (index < 0 || index >= FabricRegistry.all().size()) return;
        p.setData(BotwClimb.FABRIC, index);
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        g.fabric = index;
        broadcast(p, g.gliding, false);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
            if (!FabricUnlocks.has(p, p.getData(BotwClimb.FABRIC))) p.setData(BotwClimb.FABRIC, 0); // wearing one that is not learned (before cloths existed): back to ordinary
            g.fabric = p.getData(BotwClimb.FABRIC);
            broadcast(p, false, false);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            p.getData(BotwClimb.PARAGLIDER).fabric = p.getData(BotwClimb.FABRIC);
            broadcast(p, false, false);
        }
    }

    /** Handles a client toggle request. Closing is always fine; opening must pass the same checks as the client. */
    public static void serverToggle(ServerPlayer p, boolean open) {
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        if (open == g.gliding) return;
        if (open && p.getData(BotwClimb.CLIMB).sExhausted) { // out of stamina on the server
            broadcast(p, false, true);
            return;
        }
        boolean fromElytra = open && canSwitchFromElytra(p);
        if (fromElytra) p.stopFallFlying(); // ends the rocket boost too: fireworks only push a fall-flying player
        // fallDistance > 0: the player just left the ground and the position packet saying so is still on its way
        if (open && !(fromElytra || canDeploy(p) || (stateAllows(p) && p.fallDistance > 0))) {
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
        if (!stateAllows(p) || g.groundTicks >= SERVER_GROUND_GRACE || p.getData(BotwClimb.CLIMB).sExhausted) { // the server's stamina account ran out
            g.gliding = false;
            broadcast(p, false, true);
        } else {
            p.fallDistance = 0;
        }
    }

    /** Development command: /cotm paraglider fabric ID picks the fabric of the player running it. */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cotm").then(Commands.literal("paraglider").requires(com.botwclimb.common.Compat.gamemaster())
                .then(Commands.literal("fabric").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                FabricRegistry.all().stream().map(FabricDefinition::id), b))
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            int index = FabricRegistry.indexOf(id);
                            if (index < 0) {
                                ctx.getSource().sendFailure(Component.literal("Unknown fabric: " + id));
                                return 0;
                            }
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            forceFabric(p, index);
                            ctx.getSource().sendSuccess(() -> Component.literal("Paraglider fabric: " + id), false);
                            return 1;
                        })))));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target) {
            ParagliderData g = target.getData(BotwClimb.PARAGLIDER);
            PacketDistributor.sendToPlayer(viewer, new ParagliderStatePayload(target.getId(), g.gliding, false, g.fabric));
        }
    }
}
