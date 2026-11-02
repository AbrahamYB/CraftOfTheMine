package com.botwclimb.client;

import com.botwclimb.BotwClimb;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Minecraft 1.21.2 and later: attaches the living entity to the render state it is drawn from, so the layers, the pose and the render
 * events can find it (see {@link RenderData}). Older versions pass the entity around directly and need nothing.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RenderStateSetup {
    private RenderStateSetup() {}

    //? if >=1.21.2 {
    /*@SuppressWarnings({"unchecked", "rawtypes"})
    @net.neoforged.bus.api.SubscribeEvent
    public static void register(net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent event) {
        event.<net.minecraft.world.entity.LivingEntity, net.minecraft.client.renderer.entity.state.LivingEntityRenderState>registerEntityModifier(
                (Class) net.minecraft.client.renderer.entity.LivingEntityRenderer.class, (entity, state) -> state.setRenderData(RenderData.ENTITY, entity));
    }
    *///?}
}
