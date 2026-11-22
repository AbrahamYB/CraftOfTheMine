package com.botwclimb.client.shrine.totk;

import com.botwclimb.BotwClimb;
import com.mojang.math.Axis;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashSet;
import java.util.Set;

/** In the Tears of the Kingdom ride the rider spins about their own axis as the ribbons wind up; this turns the drawn player (everything about them, layers included) by the angle TotkRide keeps. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class TotkSpin {
    /** Players whose pose was turned in Pre and must be put back in Post. */
    private static final Set<Integer> TURNED = new HashSet<>();

    private TotkSpin() {}

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre event) {
        if (TotkRide.drawingHud()) return; // the void's own rider is turned by the void, smoothly
        var rider = com.botwclimb.client.RenderData.player(event);
        if (rider == null) return;
        if (com.botwclimb.client.shrine.MudHandClient.get(rider.getId()) != null) // all ride long the head follows the view and the body comes round after it (see body)
            com.botwclimb.client.RenderData.setBodyYaw(event, rider, body(rider, event.getPartialTick()));
        float angle = TotkRide.spin(rider.getId(), event.getPartialTick()), sunk = TotkRide.sunk(rider, event.getPartialTick());
        if (angle == 0f && sunk == 0f) return;
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0f, -sunk, 0f); // a Return rider stands waist deep in the marker before the pull
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(angle));
        TURNED.add(rider.getId());
    }

    /** Each rider's body turn {before this tick, now}: it eases toward the way they look, never more than BODY_LAG behind, and settles facing it (vanilla's own lags 50 degrees and stays there, which showed the rider sideways). */
    private static final java.util.Map<Integer, float[]> BODY = new java.util.HashMap<>();
    private static final float BODY_LAG = 60f, BODY_EASE = 0.2f;

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) { BODY.clear(); return; }
        BODY.keySet().removeIf(id -> com.botwclimb.client.shrine.MudHandClient.get(id) == null);
        for (int id : com.botwclimb.client.shrine.MudHandClient.all().keySet()) {
            if (!(mc.level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity le)) continue;
            float view = le.getYHeadRot();
            float[] b = BODY.computeIfAbsent(id, k -> new float[] {view, view});
            b[0] = b[1];
            float off = net.minecraft.util.Mth.wrapDegrees(view - b[1]) * (1f - BODY_EASE);
            b[1] = view - net.minecraft.util.Mth.clamp(off, -BODY_LAG, BODY_LAG);
        }
    }

    private static float body(net.minecraft.world.entity.player.Player rider, float partial) {
        float[] b = BODY.get(rider.getId());
        return b == null ? rider.getYHeadRot() : net.minecraft.util.Mth.rotLerp(partial, b[0], b[1]);
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event) {
        var rider = com.botwclimb.client.RenderData.player(event);
        if (rider != null && TURNED.remove(rider.getId())) event.getPoseStack().popPose();
    }
}
