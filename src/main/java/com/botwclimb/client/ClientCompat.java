package com.botwclimb.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The client calls whose shape differs between the Minecraft versions this mod is built for, each in one place (Stonecutter picks the
 * right branch per version). See {@link com.botwclimb.common.Compat} for the shared side.
 */
public final class ClientCompat {
    private ClientCompat() {}

    /** The fraction of the current tick that has passed, for smooth drawing. */
    public static float partialTick() {
        //? if >=1.21.2 {
        /*return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        *///?} else {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        //?}
    }

    // ---- the movement input (the keys as the player entity will see them)

    //? if >=1.21.2 {
    /*public static boolean jumping(net.minecraft.client.player.ClientInput in) {
        return in.keyPresses.jump();
    }

    public static void jumping(net.minecraft.client.player.ClientInput in, boolean v) {
        var k = in.keyPresses;
        in.keyPresses = new net.minecraft.world.entity.player.Input(k.forward(), k.backward(), k.left(), k.right(), v, k.shift(), k.sprint());
    }

    public static boolean shift(net.minecraft.client.player.ClientInput in) {
        return in.keyPresses.shift();
    }

    public static void shift(net.minecraft.client.player.ClientInput in, boolean v) {
        var k = in.keyPresses;
        in.keyPresses = new net.minecraft.world.entity.player.Input(k.forward(), k.backward(), k.left(), k.right(), k.jump(), v, k.sprint());
    }

    public static void clearDirections(net.minecraft.client.player.ClientInput in) {
        var k = in.keyPresses;
        in.keyPresses = new net.minecraft.world.entity.player.Input(false, false, false, false, k.jump(), k.shift(), k.sprint());
    }
    *///?} else {
    public static boolean jumping(net.minecraft.client.player.Input in) {
        return in.jumping;
    }

    public static void jumping(net.minecraft.client.player.Input in, boolean v) {
        in.jumping = v;
    }

    public static boolean shift(net.minecraft.client.player.Input in) {
        return in.shiftKeyDown;
    }

    public static void shift(net.minecraft.client.player.Input in, boolean v) {
        in.shiftKeyDown = v;
    }

    public static void clearDirections(net.minecraft.client.player.Input in) {
        in.up = in.down = in.left = in.right = false;
    }
    //?}

    // the walking impulses of the movement input
    //? if >=1.21.11 {
    /*public static float forward(net.minecraft.client.player.ClientInput in) {
        return in.getMoveVector().y;
    }

    public static float strafe(net.minecraft.client.player.ClientInput in) {
        return in.getMoveVector().x;
    }

    public static void stopMoving(net.minecraft.client.player.ClientInput in) {
        in.moveVector = net.minecraft.world.phys.Vec2.ZERO; // public through this version's access transformer
    }
    *///?} else if >=1.21.2 {
    /*public static float forward(net.minecraft.client.player.ClientInput in) {
        return in.forwardImpulse;
    }

    public static float strafe(net.minecraft.client.player.ClientInput in) {
        return in.leftImpulse;
    }

    public static void stopMoving(net.minecraft.client.player.ClientInput in) {
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
    }
    *///?} else {
    public static float forward(net.minecraft.client.player.Input in) {
        return in.forwardImpulse;
    }

    public static float strafe(net.minecraft.client.player.Input in) {
        return in.leftImpulse;
    }

    public static void stopMoving(net.minecraft.client.player.Input in) {
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
    }
    //?}

    // ---- drawing

    /** Draws part of a texture (the old blit argument order: size on screen, then the texture window). */
    public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
        //? if >=1.21.2 {
        /*g.blit(net.minecraft.client.renderer.RenderType::guiTextured, tex, x, y, u, v, w, h, uw, vh, texW, texH);
        *///?} else {
        g.blit(tex, x, y, w, h, u, v, uw, vh, texW, texH);
        //?}
    }

    /** Draws a GUI sprite (a button background and the like). */
    public static void blitSprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        //? if >=1.21.2 {
        /*g.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured, sprite, x, y, w, h);
        *///?} else {
        g.blitSprite(sprite, x, y, w, h);
        //?}
    }

    /** Selects the plain position-and-colour shader. */
    public static void positionColorShader() {
        //? if >=1.21.2 {
        /*com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.CoreShaders.POSITION_COLOR);
        *///?} else {
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        //?}
    }

    /** A pixel of a dynamic texture image (grey only: the byte order of the colour differs between versions). */
    public static void setGrey(com.mojang.blaze3d.platform.NativeImage img, int x, int y, int rgb) {
        //? if >=1.21.2 {
        /*img.setPixel(x, y, rgb);
        *///?} else {
        img.setPixelRGBA(x, y, rgb);
        //?}
    }

    /** Whether the glider (an Elytra) in the chest slot can still carry the entity. */
    public static boolean canGlide(LivingEntity e) {
        //? if >=1.21.2 {
        /*var chest = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        return chest.has(net.minecraft.core.component.DataComponents.GLIDER) && chest.getDamageValue() < chest.getMaxDamage() - 1;
        *///?} else {
        return e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).canElytraFly(e);
        //?}
    }

    /** Whether the inventory screen's recipe book is open (it shifts the screen to the right). */
    public static boolean recipeBookOpen(net.minecraft.client.gui.screens.inventory.InventoryScreen inv) {
        //? if >=1.21.2 {
        /*return inv.getGuiLeft() != (inv.width - inv.getXSize()) / 2;
        *///?} else {
        return inv.getRecipeBookComponent().isVisible();
        //?}
    }

    /** Sends a message to the server. */
    public static void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        //? if >=1.21.11 {
        /*net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
        *///?} else {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?}
    }

    /** The native window handle (for the raw keyboard state). */
    public static long windowHandle() {
        //? if >=1.21.11 {
        /*return Minecraft.getInstance().getWindow().handle();
        *///?} else {
        return Minecraft.getInstance().getWindow().getWindow();
        //?}
    }
}
