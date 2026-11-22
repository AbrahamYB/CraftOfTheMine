package com.botwclimb.client.guide;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The guide: short animated scenes about one block, on a small diorama that can be turned with the mouse, with notes pointing at
 * what they describe and a timeline underneath. The way it is laid out (the plate, blocks dropping in, notes with a line to a
 * point, the timeline and "Up next") follows Create's Ponder (MIT, by the Create Team); the code is our own.
 */
public class GuideScreen extends Screen {
    /** One scene: a title, an item (shown under "Up next"), how long it runs (ticks), its timeline marks, and what it draws at a time. */
    public interface Scene {
        Component title();

        ItemStack icon();

        int length();

        int[] marks();

        void draw(GuideScreen s, float t);
    }

    private static final int PLATE = 5, BAR_W = 220, BOX = 20;
    //? if >=26.1 {
    /*private static final net.minecraft.client.renderer.block.model.BlockDisplayContext BLOCK_CONTEXT = net.minecraft.client.renderer.block.model.BlockDisplayContext.create();
    *///?}
    private static final int TEXT_BG = 0xF0101214, TEXT_EDGE = 0xFFE8EEF2, MUTED = 0xFF9AA3AE, GREEN = 0xFF00F25A;
    /** For recordings: when set, the scene clock reads this instead of the real time. */
    public static float clockOverride = -1f;

    private final List<Scene> scenes;
    private final Screen parent;
    private int index;
    private long start = Util.getMillis();
    private float pausedAt = -1f; // the tick the scene is held at, or -1 while it plays
    private float yaw = -35f, pitch = 30f;
    private boolean dragging;
    /** Drawing state for the scene being drawn (valid during draw()). */
    private GuiGraphics g;
    private PoseStack ps;
    //? if >=26.2 {
    /*private net.minecraft.client.renderer.SubmitNodeCollector nodes;
    *///?} else {
    private MultiBufferSource.BufferSource buf;
    //?}
    private Matrix4f world, worldBase;
    private final List<Runnable> overlay = new ArrayList<>();
    /** False while a pass only places things (where the notes go), true while the scene's shapes are drawn. */
    private boolean shapes = true;
    /** Screen pixels per menu pixel in the pose being drawn to: 1 in the menu itself, the GUI scale inside a picture in picture. */
    private float pixelScale = 1f;

    public GuideScreen(List<Scene> scenes, int first, Screen parent) {
        super(Component.translatable("gui.botwclimb.guide.title"));
        this.scenes = scenes;
        this.index = first;
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private Scene scene() {
        return scenes.get(index);
    }

    /** Ticks since the scene began, held at its end. */
    public float time() {
        float t = clockOverride >= 0 ? clockOverride : pausedAt >= 0 ? pausedAt : (Util.getMillis() - start) / 50f;
        return Math.min(t, scene().length());
    }

    private void restart(int i) {
        index = Mth.clamp(i, 0, scenes.size() - 1);
        start = Util.getMillis();
        pausedAt = -1f;
    }

    private void togglePause() {
        if (pausedAt < 0) pausedAt = time();
        else {
            start = Util.getMillis() - (long) (pausedAt * 50);
            pausedAt = -1f;
        }
    }

    // ---- what scenes can draw

    /** A block of the scene, dropping into place from above once {@code from} is reached. */
    public void block(BlockState state, int x, int y, int z, float t, float from) {
        if (t < from) return;
        if (!shapes) return;
        float drop = 1.6f * (float) Math.pow(1f - Math.min(1f, (t - from) / 8f), 3);
        ps.pushPose();
        ps.translate(x, y + drop, z);
        //? if >=26.1 {
        /*var model = new net.minecraft.client.renderer.block.BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(model, state, BLOCK_CONTEXT);
        submit(c -> model.submit(ps, c, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0));
        *///?} else {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ps, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        //? if >=1.21.11 {
        /*buf.endBatch(); // drawn now, before the rings over it: from 1.21.11 those are see-through quads that a later block would cover
        *///?}
        //?}
        ps.popPose();
    }

    /** The altar at a block of the scene, with its rings lit for {@code upgrades}, dropping in like {@link #block}. */
    public void altar(BlockState state, int x, int y, int z, float t, float from, int upgrades) {
        block(state, x, y, z, t, from);
        if (t < from) return;
        float drop = 1.6f * (float) Math.pow(1f - Math.min(1f, (t - from) / 8f), 3);
        float top = y + drop + 8f / 16f + 0.01f;
        float[][] rings = {{0.26f, 0.355f}, {0.125f, 0.195f}}; // the altar's own rings: the inner one fills first
        for (int r = 0; r < 2; r++)
            for (int q = 0; q < 4; q++) {
                boolean on = upgrades > (1 - r) * 4 + q;
                arc(x + 0.5f, top, z + 0.5f, rings[r][0], rings[r][1], q * 0.25f + 0.01f, (q + 1) * 0.25f - 0.01f, on ? 0xFF00F25A : 0xE6175126);
            }
        // the rune on each side, lit from the first upgrade, as in the world
        int rune = upgrades >= 1 ? 0xFF00F25A : 0xE6175126;
        float o = 0.01f, base = y + drop, c = 1f / 16f;
        int[][] cells = {{5, 6}, {5, 7}, {5, 8}, {5, 9}, {6, 5}, {6, 10}, {7, 6}, {7, 7}, {7, 8}, {7, 9}};
        for (int[] cell : cells) {
            float y1 = base + (9 - cell[0]) * c, y0 = y1 - c, u0 = cell[1] * c, u1 = u0 + c;
            side(x + u0, z - o, x + u1, z - o, y0, y1, rune);
            side(x + 1 - u1, z + 1 + o, x + 1 - u0, z + 1 + o, y0, y1, rune);
            side(x - o, z + 1 - u1, x - o, z + 1 - u0, y0, y1, rune);
            side(x + 1 + o, z + u0, x + 1 + o, z + u1, y0, y1, rune);
        }
    }

    /** An upright quad of one colour from (x0, z0) to (x1, z1), between heights y0 and y1, seen from both sides. */
    public void side(float x0, float z0, float x1, float z1, float y0, float y1, int argb) {
        if (!shapes) return;
        quads((v, m) -> {
            vertex(v, m, x0, y0, z0, argb);
            vertex(v, m, x1, y0, z1, argb);
            vertex(v, m, x1, y1, z1, argb);
            vertex(v, m, x0, y1, z0, argb);
            vertex(v, m, x0, y1, z0, argb);
            vertex(v, m, x1, y1, z1, argb);
            vertex(v, m, x1, y0, z1, argb);
            vertex(v, m, x0, y0, z0, argb);
        });
    }

    /** An item in the scene, always turned to face the viewer whichever way the diorama is turned (an item side-on is a thin line). */
    public void item(ItemStack stack, float x, float y, float z, float scale) {
        if (!shapes) return;
        if (scale <= 0.01f) return;
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw)); // undo the view's turn and tilt
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));
        ps.scale(scale, scale, scale);
        //? if >=1.21.9 {
        /*var mc = Minecraft.getInstance();
        var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, net.minecraft.world.item.ItemDisplayContext.GROUND, mc.level, null, 0);
        submit(c -> state.submit(ps, c, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0));
        *///?} else {
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, Minecraft.getInstance().level, 0);
        //?}
        ps.popPose();
    }

    /** Something drawn flat over the scene at a fixed place on the screen (a panel), with its fade. */
    public void panel(float t, float from, float to, java.util.function.BiConsumer<GuiGraphics, Float> draw) {
        if (t < from || t > to) return;
        float a = Math.min(1f, Math.min((t - from) / 6f, (to - t) / 6f));
        if (a < 0.03f) return;
        overlay.add(() -> draw.accept(g, a));
    }

    /** The base plate, a checker of two blocks, dropping in row by row from {@code from}. */
    public void plate(BlockState a, BlockState b, float t, float from) {
        for (int x = 0; x < PLATE; x++)
            for (int z = 0; z < PLATE; z++) block((x + z) % 2 == 0 ? a : b, x, 0, z, t, from + (x + z) * 0.6f);
    }

    /** The player (when in a world) standing at a point of the scene, facing the viewer's side. */
    public void player(float x, float y, float z, float facing) {
        if (!shapes) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var p = mc.player;
        float body = p.yBodyRot, bodyO = p.yBodyRotO, head = p.yHeadRot, headO = p.yHeadRotO, yr = p.getYRot(), yrO = p.yRotO, xr = p.getXRot(), xrO = p.xRotO;
        p.yBodyRot = p.yBodyRotO = p.yHeadRot = p.yHeadRotO = facing;
        p.setYRot(facing);
        p.yRotO = facing;
        p.setXRot(0f);
        p.xRotO = 0f;
        var d = mc.getEntityRenderDispatcher();
        //? if >=1.21.9 {
        /*var state = d.extractEntity(p, 1f);
        state.shadowRadius = 0f; // no shadow under the guide's player
        state.shadowPieces.clear();
        submit(c -> d.submit(state, new net.minecraft.client.renderer.state.CameraRenderState(), x, y, z, ps, c));
        *///?} else if >=1.21.2 {
        /*d.setRenderShadow(false);
        d.render(p, x, y, z, 1f, ps, buf, LightTexture.FULL_BRIGHT);
        d.setRenderShadow(true);
        *///?} else {
        d.setRenderShadow(false);
        d.render(p, x, y, z, 0f, 1f, ps, buf, LightTexture.FULL_BRIGHT);
        d.setRenderShadow(true);
        //?}
        p.yBodyRot = body;
        p.yBodyRotO = bodyO;
        p.yHeadRot = head;
        p.yHeadRotO = headO;
        p.setYRot(yr);
        p.yRotO = yrO;
        p.setXRot(xr);
        p.xRotO = xrO;
    }

    /** A flat ring lying on a level of the scene (for glows on the ground). */
    public void ring(float cx, float y, float cz, float r0, float r1, int argb) {
        if (!shapes) return;
        quads((v, m) -> {
            int n = 40;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                vertex(v, m, cx + r0 * Math.cos(a0), y, cz + r0 * Math.sin(a0), argb);
                vertex(v, m, cx + r0 * Math.cos(a1), y, cz + r0 * Math.sin(a1), argb);
                vertex(v, m, cx + r1 * Math.cos(a1), y, cz + r1 * Math.sin(a1), argb);
                vertex(v, m, cx + r1 * Math.cos(a0), y, cz + r1 * Math.sin(a0), argb);
            }
        });
    }

    /** A flat arc lying on a level of the scene, from fraction {@code from} to {@code to} of a turn. */
    public void arc(float cx, float y, float cz, float r0, float r1, float from, float to, int argb) {
        if (!shapes) return;
        quads((v, m) -> {
            int n = Math.max(2, (int) ((to - from) * 48));
            for (int i = 0; i < n; i++) {
                double a0 = (from + (to - from) * i / n) * Math.PI * 2, a1 = (from + (to - from) * (i + 1) / n) * Math.PI * 2;
                vertex(v, m, cx - r0 * Math.sin(a0), y, cz - r0 * Math.cos(a0), argb);
                vertex(v, m, cx - r1 * Math.sin(a0), y, cz - r1 * Math.cos(a0), argb);
                vertex(v, m, cx - r1 * Math.sin(a1), y, cz - r1 * Math.cos(a1), argb);
                vertex(v, m, cx - r0 * Math.sin(a1), y, cz - r0 * Math.cos(a1), argb);
            }
        });
    }

    /** A flat quad on a level of the scene, from (x0, z0) to (x1, z1). */
    public void flat(float x0, float z0, float x1, float z1, float y, int argb) {
        if (!shapes) return;
        quads((v, m) -> {
            vertex(v, m, x0, y, z0, argb);
            vertex(v, m, x0, y, z1, argb);
            vertex(v, m, x1, y, z1, argb);
            vertex(v, m, x1, y, z0, argb);
        });
    }

    /** A vertical quad from (x0, z0) to (x1, z1), between heights y0 and y1, its colour fading out upward. */
    public void wall(float x0, float z0, float x1, float z1, float y0, float y1, int argb) {
        if (!shapes) return;
        quads((v, m) -> {
            int top = argb & 0x00FFFFFF;
            vertex(v, m, x0, y0, z0, argb);
            vertex(v, m, x1, y0, z1, argb);
            vertex(v, m, x1, y1, z1, top);
            vertex(v, m, x0, y1, z0, top);
        });
    }

    /** Coloured quads in the scene's pose: straight into the buffers, or (from 26.2) handed to the collector, which draws them later. */
    private void quads(java.util.function.BiConsumer<VertexConsumer, Matrix4f> draw) {
        //? if >=26.2 {
        /*nodes.submitCustomGeometry(ps, RenderType.debugQuads(), (pose, v) -> draw.accept(v, pose.pose()));
        *///?} else {
        draw.accept(buf.getBuffer(RenderType.debugQuads()), ps.last().pose());
        //?}
    }

    //? if >=1.21.9 {
    /*/^* Hands models (blocks, items, the player) to the collector: the picture's own from 26.2, before that the game's, drawn at once. ^/
    private void submit(java.util.function.Consumer<net.minecraft.client.renderer.SubmitNodeCollector> what) {
        //? if >=26.2 {
        /^what.accept(nodes);
        ^///?} else {
        var features = Minecraft.getInstance().gameRenderer.getFeatureRenderDispatcher();
        what.accept(features.getSubmitNodeStorage());
        features.renderAllFeatures();
        //?}
    }
    *///?}

    private static void vertex(VertexConsumer v, Matrix4f m, double x, double y, double z, int argb) {
        v.addVertex(m, (float) x, (float) y, (float) z).setColor(argb);
    }

    /** Where a point of the scene is on the screen. */
    public float[] project(float x, float y, float z) {
        Vector4f p = world.transform(new Vector4f(x, y, z, 1f));
        return new float[] {p.x(), p.y()};
    }

    /**
     * A note shown from {@code from} to {@code to}: a box beside a point of the scene with a line to it, fading in and out.
     * {@code right} puts the box to the right of the point.
     */
    public void note(float t, float from, float to, String key, float x, float y, float z, boolean right) {
        if (t < from || t > to) return;
        float a = Math.min(1f, Math.min((t - from) / 6f, (to - t) / 6f));
        if (a < 0.03f) return; // text that faint is drawn opaque by the font
        float[] at = project(x, y, z);
        overlay.add(() -> drawNote(at, a, Component.translatable("gui.botwclimb.guide." + key), right));
    }

    /** The right-click hint: a small mouse over a point, its right button lit, from {@code from} for 20 ticks. */
    public void click(float t, float from, float x, float y, float z) {
        if (t < from || t > from + 20) return;
        float a = Math.min(1f, Math.min((t - from) / 3f, (from + 20 - t) / 3f));
        if (a < 0.03f) return;
        boolean pressed = t - from > 6 && t - from < 13;
        float[] at = project(x, y, z);
        overlay.add(() -> drawMouse(at, a, pressed));
    }

    /** Something drawn flat over the scene in screen space (a panel that animates itself). */
    public void screen(java.util.function.Consumer<GuiGraphics> draw) {
        overlay.add(() -> draw.accept(g));
    }

    /** Moves the whole diorama sideways on the screen by {@code px}; call it before drawing anything. */
    public void slide(float px) {
        ps.last().pose().translateLocal(px * pixelScale, 0, 0);
        world.translateLocal(px, 0, 0);
    }

    /** How far right the diorama must move for the plate's left edge to clear screen x {@code px} (0 when it already does). */
    public float clearOf(float px) {
        float min = Float.MAX_VALUE;
        for (int c = 0; c < 8; c++) min = Math.min(min, project((c & 1) * PLATE, (c >> 1 & 1), (c >> 2 & 1) * PLATE)[0]);
        return Math.max(0f, px - min);
    }

    /** Something drawn flat on top of the scene at a point of it (for the stamina wheel), given its screen position. */
    public void flatAt(float x, float y, float z, java.util.function.BiConsumer<GuiGraphics, float[]> draw) {
        float[] at = project(x, y, z);
        overlay.add(() -> draw.accept(g, at));
    }

    // ---- the screen

    /** The view: the plate in the middle of the menu (a little low), scaled to fit, turned to the viewer's angle. */
    private void setUp(float z) {
        ps.translate(width / 2f, height / 2f + 8f, z);
        float s = Math.min(width, height) / 9.5f;
        ps.scale(s, -s, s);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(-PLATE / 2f, -0.5f, -PLATE / 2f);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        float t = time();
        float fade = Math.min(1f, t / 8f);
        this.g = g;
        overlay.clear();
        //? if >=1.21.6 {
        /*// from 1.21.6 a menu cannot draw 3D itself: this pass only works out where things are (the notes, the table, the slide), and
        // the scene is drawn into a picture in picture (Pip) that fills the menu
        ps = new PoseStack();
        shapes = false;
        pixelScale = 1f;
        setUp(0f);
        world = new Matrix4f(ps.last().pose());
        worldBase = new Matrix4f(world);
        scene().draw(this, t);
        shapes = true;
        g.submitPictureInPictureRenderState(new Pip.State(this, t, 0, 0, width, height));
        g.nextStratum(); // the notes, the table and the timeline over the picture
        for (Runnable r : overlay) r.run();
        if (fade >= 0.03f) timeline(mx, my, t, fade);
        *///?} else {
        ps = g.pose();
        buf = Minecraft.getInstance().renderBuffers().bufferSource(); // the buffers GuiGraphics draws with (it has no getter from 1.21.2)
        ps.pushPose();
        setUp(400f);
        world = new Matrix4f(ps.last().pose());
        // light from above and in front of the diorama, in the screen's own axes (y points down): the item lights assume a flat icon and left tops dark
        com.mojang.blaze3d.systems.RenderSystem.setShaderLights(new org.joml.Vector3f(-0.3f, -0.6f, 1f).normalize(), new org.joml.Vector3f(0.3f, -0.6f, -1f).normalize());
        scene().draw(this, t);
        g.flush();
        Lighting.setupForFlatItems();
        ps.popPose();
        ps.pushPose();
        ps.translate(0, 0, 900f);
        for (Runnable r : overlay) r.run();
        if (fade >= 0.03f) {
            timeline(mx, my, t, fade);
        }
        ps.popPose();
        //?}
    }

    //? if >=1.21.6 {
    /*/^* Draws the guide's scene into a picture the size of the menu (how 1.21.6 and later draw 3D in menus). ^/
    //? if >=26.1 {
    /^private interface PipState extends net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState {}
    ^///?} else {
    private interface PipState extends net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState {}
    //?}

    public static final class Pip extends net.minecraft.client.gui.render.pip.PictureInPictureRenderer<Pip.State> {
        public record State(GuideScreen screen, float t, int x0, int y0, int x1, int y1) implements PipState {
            @Override
            public float scale() {
                return 1f;
            }

            @Override
            public net.minecraft.client.gui.navigation.ScreenRectangle scissorArea() {
                return null;
            }

            @Override
            public net.minecraft.client.gui.navigation.ScreenRectangle bounds() {
                return new net.minecraft.client.gui.navigation.ScreenRectangle(x0, y0, x1 - x0, y1 - y0); // the whole picture, nothing cut off
            }
        }

        //? if >=26.2 {
        /^public Pip() {}
        ^///?} else {
        public Pip(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }
        //?}

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2f;
        }

        @Override
        protected String getTextureLabel() {
            return "botwclimb_guide";
        }

        @Override
        //? if >=26.2 {
        /^protected void renderToTexture(State st, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector) {
            GuideScreen s = st.screen();
            s.nodes = collector;
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        ^///?} else {
        protected void renderToTexture(State st, PoseStack pose) {
            GuideScreen s = st.screen();
            s.buf = bufferSource;
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        //?}
            pose.scale(1f, 1f, -1f); // the picture's pose starts in the middle with depth reversed: back to the menu's own axes
            pose.translate(-s.width / 2f, -s.height / 2f, 0f);
            s.ps = pose;
            s.pixelScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
            s.world = new Matrix4f(s.worldBase); // the placing pass already slid it; slide again from where it began
            s.setUp(0f);
            s.scene().draw(s, st.t());
            s.pixelScale = 1f;
        }
    }

    @net.neoforged.fml.common.EventBusSubscriber(modid = com.botwclimb.BotwClimb.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
    public static final class PipSetup {
        @net.neoforged.bus.api.SubscribeEvent
        public static void register(net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent event) {
            event.register(Pip.State.class, Pip::new);
        }
    }
    *///?}

    private int barX() {
        return (width - BAR_W) / 2;
    }

    private int barY() {
        return height - 14;
    }

    private void timeline(int mx, int my, float t, float a) {
        int x = barX(), y = barY();
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + 4, alpha(0xFF000000, a));
        g.fill(x, y, x + BAR_W, y + 3, alpha(0xFF2A3038, a));
        g.fill(x, y, x + (int) (BAR_W * t / scene().length()), y + 3, alpha(GREEN, a));
        for (int m : scene().marks()) {
            int mxp = x + BAR_W * m / scene().length();
            g.fill(mxp, y - 1, mxp + 1, y + 4, alpha(0xFFE8EEF2, a));
        }
        // the buttons over the bar: back, replay, pause (play while paused), close, next
        for (int i = 0; i < 5; i++) {
            boolean usable = i == 0 ? index > 0 : i != 4 || index < scenes.size() - 1;
            int bx = buttonX(i), by = y - 6 - BOX;
            boolean hover = usable && mx >= bx && mx < bx + BOX && my >= by && my < by + BOX;
            g.fill(bx, by, bx + BOX, by + BOX, alpha(hover ? 0xFFE8EEF2 : 0xFF000000, a));
            g.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, alpha(hover ? 0xFF2E3640 : 0xFF14181D, a));
            icon(i == 2 && pausedAt >= 0 ? PLAY : ICONS[i], bx + 6, by + 6, alpha(usable ? 0xFFFFFFFF : 0xFF4A525C, a));
        }
        // at the end: what comes next
        if (t >= scene().length() - 0.01f && index < scenes.size() - 1) {
            Component next = scenes.get(index + 1).title();
            int w = Math.max(font.width(next), font.width(Component.translatable("gui.botwclimb.guide.next"))) + 16, bx = width / 2 - w / 2, by = y - 6 - BOX - 30;
            box(bx, by, w, 24, 1f);
            g.drawCenteredString(font, Component.translatable("gui.botwclimb.guide.next"), width / 2, by + 3, MUTED);
            g.drawCenteredString(font, next, width / 2, by + 13, 0xFFFFFFFF);
        }
    }

    /** The button icons, 8 by 8: back, replay, pause, close, next; and play, shown in pause's place while paused. */
    private static final String[][] ICONS = {
            {"....##..", "...###..", "..####..", ".#####..", ".#####..", "..####..", "...###..", "....##.."},
            {"..###.#.", ".#...##.", "#...###.", "#.......", "#......#", "#......#", ".#....#.", "..####.."},
            {".##..##.", ".##..##.", ".##..##.", ".##..##.", ".##..##.", ".##..##.", ".##..##.", ".##..##."},
            {"##....##", "###..###", ".######.", "..####..", "..####..", ".######.", "###..###", "##....##"},
            {"..##....", "..###...", "..####..", "..#####.", "..#####.", "..####..", "..###...", "..##...."}};
    private static final String[] PLAY = {".#......", ".##.....", ".###....", ".####...", ".####...", ".###....", ".##.....", ".#......"};

    private void icon(String[] rows, int x, int y, int colour) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++)
                if (rows[r].charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, colour);
    }

    private int buttonX(int i) {
        int[] off = {-66, -38, -10, 18, 46};
        return width / 2 + off[i];
    }

    private void box(int x, int y, int w, int h, float a) {
        g.fill(x, y, x + w, y + h, alpha(TEXT_EDGE, a));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, alpha(TEXT_BG, a));
    }

    private void drawNote(float[] at, float a, Component text, boolean right) {
        List<FormattedCharSequence> lines = font.split(text, 130);
        int w = 0;
        for (var l : lines) w = Math.max(w, font.width(l));
        w += 10;
        int h = lines.size() * 10 + 7;
        int px = Math.round(at[0]), py = Math.round(at[1]);
        int bx = right ? px + 40 : px - 40 - w, by = py - h / 2 - 14;
        bx = Mth.clamp(bx, 4, width - w - 4);
        by = Mth.clamp(by, 44, height - h - 50);
        int lineEnd = right ? bx : bx + w;
        int ly = by + h / 2;
        // the line: from the point up or down to the box's middle, then across
        g.fill(Math.min(px, lineEnd), ly, Math.max(px, lineEnd), ly + 1, alpha(TEXT_EDGE, a));
        g.fill(px, Math.min(py, ly), px + 1, Math.max(py, ly) + 1, alpha(TEXT_EDGE, a));
        g.fill(px - 1, py - 1, px + 2, py + 2, alpha(TEXT_EDGE, a));
        box(bx, by, w, h, a);
        for (int i = 0; i < lines.size(); i++) g.drawString(font, lines.get(i), bx + 5, by + 4 + i * 10, alpha(0xFFFFFFFF, a), false);
    }

    private void drawMouse(float[] at, float a, boolean pressed) {
        int x = Math.round(at[0]) - 6, y = Math.round(at[1]) - 34;
        g.fill(x, y, x + 13, y + 18, alpha(0xFF000000, a));
        g.fill(x + 1, y + 1, x + 12, y + 17, alpha(0xFFE8EEF2, a));
        g.fill(x + 1, y + 7, x + 12, y + 8, alpha(0xFF000000, a));
        g.fill(x + 6, y + 1, x + 7, y + 8, alpha(0xFF000000, a));
        g.fill(x + 7, y + 1, x + 12, y + 7, alpha(pressed ? GREEN : 0xFF7D8694, a));
        g.drawCenteredString(font, Component.translatable("gui.botwclimb.guide.right_click"), x + 7, y - 11, alpha(0xFFFFFFFF, a));
    }

    private static int alpha(int argb, float a) {
        return ((int) ((argb >>> 24) * Mth.clamp(a, 0f, 1f)) << 24) | (argb & 0x00FFFFFF);
    }

    // ---- input

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        return mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return mouseReleased(event.x(), event.y(), event.button());
    }
    *///?}

    //? if <1.21.9
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        int by = barY() - 6 - BOX;
        for (int i = 0; i < 5; i++) {
            if (mx < buttonX(i) || mx >= buttonX(i) + BOX || my < by || my >= by + BOX) continue;
            switch (i) {
                case 0 -> restart(index - 1);
                case 1 -> restart(index);
                case 2 -> togglePause();
                case 3 -> onClose();
                default -> restart(index + 1);
            }
            return true;
        }
        if (my >= barY() - 3 && my < barY() + 6 && mx >= barX() && mx < barX() + BAR_W) { // jump along the timeline
            float to = (float) ((mx - barX()) / BAR_W * scene().length());
            start = Util.getMillis() - (long) (to * 50);
            if (pausedAt >= 0) pausedAt = to; // paused, it shows the picked moment and stays there
            return true;
        }
        dragging = true;
        return true;
    }

    //? if <1.21.9
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        yaw += (float) dx * 1.2f;
        pitch = Mth.clamp(pitch + (float) dy * 0.8f, 5f, 80f);
        return true;
    }

    //? if <1.21.9
    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return true;
    }
}
