package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.TerminalRidePayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * What the client knows about terminal rides: who is riding, in which phase, and since when. Also locks the rider's own
 * controls and keeps their camera in third person for the whole ride.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class MudHandClient {
    /** since: game tick the ride began; phaseSince: game tick of the latest phase change. */
    public record State(int phase, long since, long phaseSince, float startY, int kind) {
        /** True while the rider is being carried up into the sky (as against sinking through the ground, or coming down). */
        public boolean rising() {
            return phase == 2 && kind == 0;
        }

        public boolean sinking() {
            return phase == 2 && kind == 1;
        }
    }

    private static final Map<Integer, State> RIDES = new HashMap<>();
    private static final long MAX_RIDE_TICKS = 600;
    /** How far the Tears of the Kingdom Return pulls the rider into the ground before the transition, in blocks. */
    private static final double PULL_DEPTH = 1.0;
    private static CameraType saved;

    private MudHandClient() {}

    public static java.util.Map<Integer, State> all() {
        return java.util.Collections.unmodifiableMap(RIDES);
    }

    public static State get(int entityId) {
        return RIDES.get(entityId);
    }

    public static void onState(TerminalRidePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.player != null && p.entityId() == mc.player.getId()) {
            if (p.phase() != 0 && saved == null) {
                saved = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            } else if (p.phase() == 0 && saved != null) {
                mc.options.setCameraType(saved);
                saved = null;
            }
        }
        if (p.phase() == 0) {
            RIDES.remove(p.entityId());
            return;
        }
        long now = mc.level.getGameTime();
        State old = RIDES.get(p.entityId());
        RIDES.put(p.entityId(), new State(p.phase(), old == null ? now : old.since(), now, p.startY(), p.kind()));
    }

    /**
     * A Return ride sinks through the ground. The server trusts where the player says they are, so the player's own client moves
     * them: straight down, past anything in the way, a little faster each tick. (Rising is done by the server's velocity.)
     */
    @SubscribeEvent
    public static void sink(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.getEntity().level().isClientSide() || event.getEntity() != mc.player || mc.level == null) return;
        State s = RIDES.get(mc.player.getId());
        if (s != null && s.phase() == 1 && s.kind() == 1) {
            // the Return: before anything else the ground takes hold and draws the rider part-way in (about a
            // block, down to the waist), slowly at first and then firmly, over the last 20 ticks of the gathering
            double pull = Math.max(0, Math.min(1, (mc.level.getGameTime() - s.since() - 20) / 20.0));
            mc.player.setPos(mc.player.getX(), s.startY() - PULL_DEPTH * pull * pull * (3 - 2 * pull), mc.player.getZ());
            mc.player.setDeltaMovement(0, 0, 0);
            return;
        }
        if (s == null || !s.sinking()) return;
        double t = mc.level.getGameTime() - s.phaseSince() - com.botwclimb.common.shrine.TerminalRide.SINK_DELAY;
        if (t < 0) return; // the ground is still unravelling
        double v = Math.min(com.botwclimb.common.shrine.TerminalRide.MAX_SPEED, com.botwclimb.common.shrine.TerminalRide.START_SPEED + com.botwclimb.common.shrine.TerminalRide.ACCEL * t);
        mc.player.setPos(mc.player.getX(), mc.player.getY() - v, mc.player.getZ());
        mc.player.setDeltaMovement(0, 0, 0);
    }

    @SubscribeEvent
    public static void lock(MovementInputUpdateEvent event) {
        if (!RIDES.containsKey(event.getEntity().getId())) return;
        var in = event.getInput();
        com.botwclimb.client.ClientCompat.clearDirections(in);
        com.botwclimb.client.ClientCompat.jumping(in, false);
        com.botwclimb.client.ClientCompat.shift(in, false);
        com.botwclimb.client.ClientCompat.stopMoving(in);
    }

    @SubscribeEvent
    public static void keepThirdPerson(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) { // a ride the server never closed must not hold the player forever
            long now = mc.level.getGameTime();
            RIDES.values().removeIf(r -> now < r.since() || now - r.since() > MAX_RIDE_TICKS);
        }
        if (saved == null) return;
        if (mc.player == null || !RIDES.containsKey(mc.player.getId())) { // left the world mid-ride
            if (mc.player != null) mc.options.setCameraType(saved);
            saved = null;
            RIDES.clear();
        } else if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }
}
