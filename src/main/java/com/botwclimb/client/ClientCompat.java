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
        //? if >=1.21.11 {
        /*g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, x, y, u, v, w, h, uw, vh, texW, texH);
        *///?} else if >=1.21.2 {
        /*g.blit(net.minecraft.client.renderer.RenderType::guiTextured, tex, x, y, u, v, w, h, uw, vh, texW, texH);
        *///?} else {
        g.blit(tex, x, y, w, h, u, v, uw, vh, texW, texH);
        //?}
    }

    /** Draws a GUI sprite (a button background and the like). */
    public static void blitSprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        //? if >=1.21.11 {
        /*g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
        *///?} else if >=1.21.2 {
        /*g.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured, sprite, x, y, w, h);
        *///?} else {
        g.blitSprite(sprite, x, y, w, h);
        //?}
    }

    /** Selects the plain position-and-colour shader (only the old immediate drawing needs it). */
    public static void positionColorShader() {
        //? if >=1.21.11 {
        /*// the GUI pipelines pick their own shader
        *///?} else if >=1.21.2 {
        /*com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.CoreShaders.POSITION_COLOR);
        *///?} else {
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
        //?}
    }

    /** Saves a picture of the main render target into a folder (dev tooling). */
    public static void screenshot(java.io.File dir, String name) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=1.21.11 {
        /*net.minecraft.client.Screenshot.grab(dir, name, mc.getMainRenderTarget(), 1, msg -> { });
        *///?} else {
        net.minecraft.client.Screenshot.grab(dir, name, mc.getMainRenderTarget(), msg -> { });
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

    /** Whether either Shift key is held right now. */
    public static boolean shiftDown() {
        long w = windowHandle();
        //? if >=1.21.11 {
        /*var win = Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        *///?} else {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        //?}
    }

    /** The raw state of a key. */
    public static boolean keyDown(int keyCode) {
        //? if >=1.21.11 {
        /*return com.mojang.blaze3d.platform.InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode);
        *///?} else {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(windowHandle(), keyCode);
        //?}
    }

    // ---- the GUI's transform (a 3D matrix stack before 1.21.6, a 2D one after; the z of a translate is dropped, drawing order decides what is on top)

    public static void pushPose(GuiGraphics g) {
        //? if >=1.21.11 {
        /*g.pose().pushMatrix();
        *///?} else {
        g.pose().pushPose();
        //?}
    }

    public static void popPose(GuiGraphics g) {
        //? if >=1.21.11 {
        /*g.pose().popMatrix();
        *///?} else {
        g.pose().popPose();
        //?}
    }

    public static void translate(GuiGraphics g, float x, float y, float z) {
        //? if >=1.21.11 {
        /*g.pose().translate(x, y);
        *///?} else {
        g.pose().translate(x, y, z);
        //?}
    }

    public static void scale(GuiGraphics g, float x, float y) {
        //? if >=1.21.11 {
        /*g.pose().scale(x, y);
        *///?} else {
        g.pose().scale(x, y, 1f);
        //?}
    }

    /** Makes the skin's outer layers (hat, sleeves, pants, jacket) follow the posed body; from 1.21.11 they are children of the parts and follow on their own. */
    public static void syncOuterLayers(net.minecraft.client.model.PlayerModel m) {
        //? if >=1.21.11 {
        /*// nothing to do
        *///?} else {
        m.hat.copyFrom(m.head);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.jacket.copyFrom(m.body);
        //?}
    }

    /** The fraction of the tick that has passed; {@code pause} true counts a frozen game as at the start of the tick. */
    public static float partialTick(boolean realtime) {
        //? if >=1.21.2 {
        /*return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(realtime);
        *///?} else {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(realtime);
        //?}
    }

    /** Where the camera is. */
    public static net.minecraft.world.phys.Vec3 cameraPos() {
        //? if >=1.21.11 {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().position();
        *///?} else {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        //?}
    }

    /** The pose stack to draw level geometry with (camera-relative positions go in; it carries the view rotation). */
    public static com.mojang.blaze3d.vertex.PoseStack levelPose(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        //? if >=1.21.11 {
        /*var ps = new com.mojang.blaze3d.vertex.PoseStack();
        ps.mulPose(event.getModelViewMatrix());
        return ps;
        *///?} else {
        return event.getPoseStack();
        //?}
    }

    /** The puff of pieces of a broken block. */
    public static void destroyEffect(Minecraft mc, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        //? if >=1.21.11 {
        /*mc.level.addDestroyBlockEffect(pos, state);
        *///?} else {
        mc.particleEngine.destroy(pos, state);
        //?}
    }

    /** A pose stack whose top is a copy of the given pose (for drawing code that wants a stack, from a geometry callback that hands out a pose). */
    public static com.mojang.blaze3d.vertex.PoseStack poseStackOf(com.mojang.blaze3d.vertex.PoseStack.Pose pose) {
        var ps = new com.mojang.blaze3d.vertex.PoseStack();
        //? if >=1.21.11 {
        /*ps.last().set(pose);
        *///?} else {
        ps.last().pose().set(pose.pose());
        ps.last().normal().set(pose.normal());
        //?}
        return ps;
    }
}
