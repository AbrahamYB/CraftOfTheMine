package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Adds the mud render layer to both player skin types. */
//? if >=1.21.11 {
/*@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
*///?} else {
@EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//?}
public final class MudHandSetup {
    private MudHandSetup() {}

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer r) {
                r.addLayer(new MudHandLayer(r));
            }
        }
    }
}
