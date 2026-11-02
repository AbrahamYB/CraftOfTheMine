package com.botwclimb.client.altar;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.AltarResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client entry points for the stamina altar: opening the screen, getting the server's answer, drawing the glow. */
public final class AltarClient {
    private AltarClient() {}

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new AltarScreen(pos));
    }

    public static void onResult(AltarResultPayload result) {
        if (result.finale()) AltarFx.onFinale();
        if (Minecraft.getInstance().screen instanceof AltarScreen screen) screen.onResult(result);
    }

    //? if >=1.21.11 {
    /*@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
    *///?} else {
    @EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    //?}
    public static final class Setup {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(BotwClimb.ALTAR_BE.get(), AltarRenderer::new);
        }

        @SubscribeEvent
        public static void particles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(BotwClimb.GLYPH.get(), SheikahGlyph.Provider::new);
        }
    }

    @EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
    public static final class Lifecycle {
        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            AltarFx.reset();
        }
    }
}
