package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Registers the canopy model and adds its render layer to both player skin types. */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ParagliderClientSetup {
    private ParagliderClientSetup() {}

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ParagliderModel.LAYER, ParagliderModel::createLayer);
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer r) {
                r.addLayer(new ParagliderLayer(r, new ParagliderModel(event.getEntityModels().bakeLayer(ParagliderModel.LAYER))));
            }
        }
    }
}
