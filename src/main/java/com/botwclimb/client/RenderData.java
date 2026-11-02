package com.botwclimb.client;

import com.botwclimb.BotwClimb;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * From Minecraft 1.21.2 on, a player is drawn from a snapshot of its state ("render state"), not from the player itself, so the entity
 * is attached to that snapshot when it is taken (see LivingEntityRendererMixin) and the renderers get it back from here.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class RenderData {
    private RenderData() {}

    /** The living entity a render event is about (any version; raw type because the event has a different number of type parameters per version). */
    public static net.minecraft.world.entity.LivingEntity entity(net.neoforged.neoforge.client.event.RenderLivingEvent event) {
        //? if >=1.21.2 {
        /*return entity(event.getRenderState());
        *///?} else {
        return event.getEntity();
        //?}
    }

    /** The player a player render event is about. */
    public static net.minecraft.world.entity.player.Player player(net.neoforged.neoforge.client.event.RenderPlayerEvent event) {
        //? if >=1.21.2 {
        /*return player(event.getRenderState());
        *///?} else {
        return event.getEntity();
        //?}
    }

    /** Sets the body yaw a living entity is drawn with, for this render only (the entity's own value is left alone from 1.21.2 on). */
    public static void setBodyYaw(net.neoforged.neoforge.client.event.RenderLivingEvent event, net.minecraft.world.entity.LivingEntity e, float yaw) {
        //? if >=1.21.2 {
        /*event.getRenderState().bodyRot = yaw;
        *///?} else {
        e.yBodyRot = yaw;
        e.yBodyRotO = yaw;
        //?}
    }

    //? if >=1.21.2 {
    /*public static final net.minecraft.util.context.ContextKey<net.minecraft.world.entity.LivingEntity> ENTITY =
            new net.minecraft.util.context.ContextKey<>(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "entity"));

    // the living entity a render state was taken from, or null
    public static net.minecraft.world.entity.LivingEntity entity(net.neoforged.neoforge.client.renderstate.BaseRenderState state) {
        return state.getRenderData(ENTITY);
    }

    // the player a render state was taken from, or null (not a player)
    public static AbstractClientPlayer player(net.neoforged.neoforge.client.renderstate.BaseRenderState state) {
        return entity(state) instanceof AbstractClientPlayer p ? p : null;
    }
    *///?}
}
