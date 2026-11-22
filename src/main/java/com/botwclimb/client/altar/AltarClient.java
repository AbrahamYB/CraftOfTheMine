package com.botwclimb.client.altar;

import com.botwclimb.BotwClimb;
import com.botwclimb.network.AltarResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client entry points for the stamina altar: opening the screen, getting the server's answer, drawing the glow. */
public final class AltarClient {
    private AltarClient() {}

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new AltarScreen(pos));
    }

    public static void onResult(AltarResultPayload result) {
        // the server is the authority: if it says everything is bought, show that even if its state never reached us (an older server build syncs differently)
        var me = Minecraft.getInstance().player;
        if (!result.ok() && "maxed".equals(result.reason()) && me != null)
            me.setData(BotwClimb.STAMINA_UPGRADES, com.botwclimb.common.climb.ClimbConstants.STAMINA_MAX_UPGRADES);
        if (Minecraft.getInstance().screen instanceof AltarScreen screen) screen.onResult(result);
    }

    //? if >=1.21.8 {
    /*@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
    *///?} else {
    @EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    //?}
    public static final class Setup {
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(BotwClimb.ALTAR_BE.get(), AltarRenderer::new);
        }

        //? if >=1.21.4 {
        /*@SubscribeEvent
        public static void phase(net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent event) {
            event.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "altar_phase"), AltarPhase.Property.CODEC);
        }
        *///?} else {
        @SubscribeEvent
        public static void phase(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) { // the item model's override reads the player's upgrades, as 0 to 1
            event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(BotwClimb.ALTAR_ITEM.get(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "altar_phase"), (stack, level, entity, seed) -> AltarPhase.value() / 8f));
        }
        //?}

        @SubscribeEvent
        public static void particles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(BotwClimb.GLYPH.get(), SheikahGlyph.Provider::new);
        }
    }
}
