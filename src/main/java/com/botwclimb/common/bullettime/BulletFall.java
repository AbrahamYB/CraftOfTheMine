package com.botwclimb.common.bullettime;

import com.botwclimb.BotwClimb;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Own fall in bullet time: vanilla applies this as a multiplier on the gravity the glider already set, so -0.925
 * leaves 7.5% of normal. A transient modifier, so nothing is saved. The server never takes the client's word for it:
 * it only applies while the player really is in the air with a bow in hand, and it is taken off again on any tick
 * where that stops being true (so a lost "off" packet cannot leave anyone in slow motion).
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID)
public final class BulletFall {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "bullet_fall");
    private static final double MODIFIER = -0.925;

    private BulletFall() {}

    /** The conditions bullet time's slow fall needs, checked the same way on the client and the server. */
    public static boolean allowed(Player p) {
        return !p.onGround() && !p.isFallFlying() && !p.getAbilities().flying && !p.getData(BotwClimb.PARAGLIDER).gliding
                && isShooter(p.getMainHandItem().getItem());
    }

    /** Bows and crossbows both give bullet time. */
    public static boolean isShooter(net.minecraft.world.item.Item item) {
        return item instanceof BowItem || item instanceof net.minecraft.world.item.CrossbowItem;
    }

    public static void apply(Player p, boolean on) {
        AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;
        boolean ok = on && allowed(p);
        if (ok && !gravity.hasModifier(ID)) {
            gravity.addTransientModifier(new AttributeModifier(ID, MODIFIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!ok) {
            gravity.removeModifier(ID);
        }
    }

    /** Server tick: no matter what the client sent, the slow fall ends the moment its conditions do. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide() || allowed(p)) return;
        AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);
        if (gravity != null && gravity.hasModifier(ID)) gravity.removeModifier(ID);
    }
}
