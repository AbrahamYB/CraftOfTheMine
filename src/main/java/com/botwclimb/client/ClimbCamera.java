package com.botwclimb.client;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.paraglider.ClientParagliderController;
import com.botwclimb.common.climb.ClimbData;
import com.botwclimb.common.climb.ClimbState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Locks the camera to third person (rear) for as long as the local player is on a wall, and puts the player's own
 * perspective back afterwards. F5 is swallowed while locked: the tick event runs before vanilla's key handling, so
 * the presses are consumed there and the perspective never changes for even a frame (no flicker).
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class ClimbCamera {
    /** After a ledge pull-up the camera stays a little longer, so the stand-up animation is seen to the end. */
    private static final int LEDGE_SETTLE_TICKS = 2;

    /** Camera pull-out / pull-in speed per tick (about 0.7 s each way). */
    private static final float ZOOM_STEP = 0.07f;      // pulling out when you grab a wall
    private static final float ZOOM_STEP_BACK = 0.476f; // pulling back in when you let go: very quick, about 0.1 s (5% slower than 0.5)

    /** The climbing camera sits this much farther behind the player than the normal third-person camera. */
    private static final float CAMERA_FARTHER = 1.25f;

    private enum Phase { OFF, ON, OUT, IN }

    private static Phase phase = Phase.OFF;
    private static CameraType saved = CameraType.FIRST_PERSON;
    private static float zoom = 1f, zoomO = 1f; // 0 = camera at the head, 1 = the normal third-person distance
    private static boolean afterLedge;
    private static int settle;

    private ClimbCamera() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (phase != Phase.OFF && (mc.player == null || mc.level == null || !mc.player.isAlive())) { // died or left the world mid-climb: straight back, no animation
            hardRestore();
            return;
        }
        boolean want = wantLocked(mc);
        zoomO = zoom;
        switch (phase) {
            case OFF -> {
                if (want) {
                    saved = mc.options.getCameraType();
                    if (saved == CameraType.THIRD_PERSON_BACK) { // already in rear view: ease out from the normal distance to the wider one
                        phase = Phase.ON;
                        zoom = zoomO = 0f;
                    } else {
                        set(mc, CameraType.THIRD_PERSON_BACK); // starts at the head and pulls out
                        zoom = zoomO = 0f;
                        phase = Phase.ON;
                    }
                }
            }
            case ON -> {
                if (!want) {
                    phase = Phase.OUT;
                } else {
                    if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK && !ClientParagliderController.bowAimActive()) {
                        set(mc, CameraType.THIRD_PERSON_BACK); // the bow aim camera may hold first person while drawing
                    }
                    zoom = Math.min(1f, zoom + (saved == CameraType.THIRD_PERSON_BACK ? ZOOM_STEP_BACK * 0.7f : ZOOM_STEP));
                }
            }
            case OUT -> {
                if (want) { // grabbed a wall again while pulling in: turn around
                    phase = Phase.ON;
                } else {
                    zoom = Math.max(0f, zoom - ZOOM_STEP_BACK);
                    if (zoom <= 0f) { // now at the head (or back at the normal distance): switch to the player's own perspective
                        if (saved != CameraType.THIRD_PERSON_BACK) set(mc, saved);
                        if (saved.isFirstPerson() || saved == CameraType.THIRD_PERSON_BACK) finish();
                        else phase = Phase.IN;
                    }
                }
            }
            case IN -> {
                zoom = Math.min(1f, zoom + ZOOM_STEP_BACK);
                if (zoom >= 1f) finish();
            }
        }
        if (phase == Phase.ON || phase == Phase.OUT) while (mc.options.keyTogglePerspective.consumeClick()) { /* swallowed */ }
    }

    private static void finish() {
        phase = Phase.OFF;
        zoom = zoomO = 1f;
        afterLedge = false;
        settle = 0;
        Minecraft mc = Minecraft.getInstance();
        while (mc.options.keyTogglePerspective.consumeClick()) { /* a press from the last moment must not toggle now */ }
    }

    /** The pull-out / pull-in: scales the third-person distance, interpolated between ticks. */
    @SubscribeEvent
    public static void onDistance(CalculateDetachedCameraDistanceEvent event) {
        if (phase == Phase.OFF) return;
        float z = Mth.lerp(ClientCompat.partialTick(), zoomO, zoom);
        z = z * z * z * (z * (z * 6f - 15f) + 10f); // eased with a gentler start and finish than a plain smoothstep
        float mult = saved == CameraType.THIRD_PERSON_BACK
                ? Mth.lerp(z, 1f, CAMERA_FARTHER)             // already third person: just ease out to the wider view
                : CAMERA_FARTHER * Math.max(0.02f, z);        // from the head (or the front view) out to the wider view
        event.setDistance(event.getDistance() * mult);
    }

    private static boolean wantLocked(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !p.isAlive() || !p.hasData(BotwClimb.CLIMB)) return false;
        ClimbData d = p.getData(BotwClimb.CLIMB);
        ClimbState s = d.state;
        if (s != ClimbState.NONE) {
            afterLedge = s == ClimbState.LEDGE_TRANSITION;
            settle = LEDGE_SETTLE_TICKS;
            return true;
        }
        if (p.hasData(BotwClimb.PARAGLIDER) && p.getData(BotwClimb.PARAGLIDER).gliding
                && ClientConfig.gliderCamera()) { // the glider uses the same view, unless the player keeps their own
            afterLedge = false;
            settle = 0;
            return true;
        }
        if (phase == Phase.ON && afterLedge && settle > 0) { // finished standing up on a ledge: let the last of the animation play
            settle--;
            return true;
        }
        afterLedge = false;
        return false;
    }

    private static void set(Minecraft mc, CameraType type) {
        CameraType before = mc.options.getCameraType();
        mc.options.setCameraType(type);
        if (before.isFirstPerson() != type.isFirstPerson()) { // what vanilla does after F5
            mc.gameRenderer.checkEntityPostEffect(type.isFirstPerson() ? mc.getCameraEntity() : null);
        }
        mc.levelRenderer.needsUpdate();
    }

    /** Disconnecting, changing world or respawning must never leave the camera locked. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hardRestore();
    }

    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.Clone event) {
        hardRestore();
    }

    /** No animation: put the player's own perspective straight back (disconnect, respawn, death). */
    private static void hardRestore() {
        if (phase == Phase.OFF || phase == Phase.IN) { finish(); return; }
        set(Minecraft.getInstance(), saved);
        finish();
    }
}
