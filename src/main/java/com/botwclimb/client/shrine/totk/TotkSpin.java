package com.botwclimb.client.shrine.totk;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.ClientConfig;
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
        if (ClientConfig.style() != ClientConfig.RideStyle.TOTK || TotkRide.drawingHud()) return; // the void's own rider is turned by the void, smoothly
        float angle = TotkRide.spin(event.getEntity().getId(), event.getPartialTick());
        if (angle == 0f) return;
        event.getPoseStack().pushPose();
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(angle));
        TURNED.add(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post event) {
        if (TURNED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }
}
