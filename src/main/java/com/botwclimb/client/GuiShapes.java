package com.botwclimb.client;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * Smooth filled shapes for the GUI (the stamina wheel's rings): triangle strips with a colour at each end, drawn straight away before
 * 1.21.6, collected and handed to the GUI renderer as one element from then on (it draws the whole GUI in one go after the screens have
 * described it). Use {@link #begin}, then any number of {@link #strip} calls, then {@link #end}.
 */
public final class GuiShapes {
    private GuiShapes() {}

    /** The corner order the GUI pipelines keep (the one vanilla's own fills use); a quad given the other way round would be culled and vanish. */
    static int[] order(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        float area = x0 * y1 - x1 * y0 + x1 * y2 - x2 * y1 + x2 * y3 - x3 * y2 + x3 * y0 - x0 * y3;
        return area <= 0 ? new int[]{0, 1, 2, 3} : new int[]{3, 2, 1, 0};
    }

    //? if >=1.21.9 {
    /*private static GuiGraphics target;
    private static org.joml.Matrix3x2f pose;
    private static final java.util.ArrayList<float[]> QUADS = new java.util.ArrayList<>(); // per quad: x y colour, four times (the colour as float bits)

    public static void begin(GuiGraphics g) {
        target = g;
        pose = new org.joml.Matrix3x2f(g.pose());
        QUADS.clear();
    }

    // the transform the old drawing path needs; here the shapes carry the GUI pose themselves, so it is only a placeholder
    public static Matrix4f pose(GuiGraphics g) {
        return new Matrix4f();
    }

    public static void strip(Matrix4f m, float cx, float cy, float ra, float rb, float from, float to, int in, int out) {
        if (rb <= ra) return;
        int n = Math.max(3, (int) Math.ceil(208 * (to - from)));
        float[] prev = null;
        for (int i = 0; i <= n; i++) {
            double ang = -Math.PI / 2 - 2 * Math.PI * (from + (to - from) * i / n);
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            float[] cur = {cx + cos * ra, cy + sin * ra, cx + cos * rb, cy + sin * rb};
            if (prev != null) {
                // one quad per step: inner edge a, outer edge b
                QUADS.add(new float[]{prev[0], prev[1], Float.intBitsToFloat(in), prev[2], prev[3], Float.intBitsToFloat(out),
                        cur[2], cur[3], Float.intBitsToFloat(out), cur[0], cur[1], Float.intBitsToFloat(in)});
            }
            prev = cur;
        }
    }

    // one quad with a colour at each corner
    public static void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        QUADS.add(new float[]{x0, y0, Float.intBitsToFloat(c0), x1, y1, Float.intBitsToFloat(c1), x2, y2, Float.intBitsToFloat(c2), x3, y3, Float.intBitsToFloat(c3)});
    }

    public static void end() {
        if (target == null || QUADS.isEmpty()) return;
        var quads = new java.util.ArrayList<>(QUADS);
        QUADS.clear();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] q : quads) for (int k = 0; k < 12; k += 3) {
            minX = Math.min(minX, q[k]);
            maxX = Math.max(maxX, q[k]);
            minY = Math.min(minY, q[k + 1]);
            maxY = Math.max(maxY, q[k + 1]);
        }
        var bounds = new net.minecraft.client.gui.navigation.ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxY - minY) + 1)
                .transformMaxBounds(pose);
        target.guiRenderState.submitGuiElement(new Shapes(quads, new org.joml.Matrix3x2f(pose), bounds));
        target = null;
    }

    private record Shapes(java.util.List<float[]> quads, org.joml.Matrix3x2f pose, net.minecraft.client.gui.navigation.ScreenRectangle bounds)
            implements net.minecraft.client.gui.render.state.GuiElementRenderState {
        @Override
        public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer vc) {
            for (float[] q : quads) {
                int[] o = order(q[0], q[1], q[3], q[4], q[6], q[7], q[9], q[10]);
                for (int c : o) vc.addVertexWith2DPose(pose, q[c * 3], q[c * 3 + 1]).setColor(Float.floatToRawIntBits(q[c * 3 + 2]));
            }
        }

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return net.minecraft.client.renderer.RenderPipelines.GUI;
        }

        @Override
        public net.minecraft.client.gui.render.TextureSetup textureSetup() {
            return net.minecraft.client.gui.render.TextureSetup.noTexture();
        }

        @Override
        public net.minecraft.client.gui.navigation.ScreenRectangle scissorArea() {
            return null;
        }
    }
    *///?} else if >=1.21.6 {
    /*private static GuiGraphics target;
    private static org.joml.Matrix3x2f pose;
    private static final java.util.ArrayList<float[]> QUADS = new java.util.ArrayList<>(); // per quad: x y colour, four times (the colour as float bits)

    public static void begin(GuiGraphics g) {
        target = g;
        pose = new org.joml.Matrix3x2f(g.pose());
        QUADS.clear();
    }

    // the transform the old drawing path needs; here the shapes carry the GUI pose themselves, so it is only a placeholder
    public static Matrix4f pose(GuiGraphics g) {
        return new Matrix4f();
    }

    public static void strip(Matrix4f m, float cx, float cy, float ra, float rb, float from, float to, int in, int out) {
        if (rb <= ra) return;
        int n = Math.max(3, (int) Math.ceil(208 * (to - from)));
        float[] prev = null;
        for (int i = 0; i <= n; i++) {
            double ang = -Math.PI / 2 - 2 * Math.PI * (from + (to - from) * i / n);
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            float[] cur = {cx + cos * ra, cy + sin * ra, cx + cos * rb, cy + sin * rb};
            if (prev != null) {
                // one quad per step: inner edge a, outer edge b
                QUADS.add(new float[]{prev[0], prev[1], Float.intBitsToFloat(in), prev[2], prev[3], Float.intBitsToFloat(out),
                        cur[2], cur[3], Float.intBitsToFloat(out), cur[0], cur[1], Float.intBitsToFloat(in)});
            }
            prev = cur;
        }
    }

    // one quad with a colour at each corner
    public static void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        QUADS.add(new float[]{x0, y0, Float.intBitsToFloat(c0), x1, y1, Float.intBitsToFloat(c1), x2, y2, Float.intBitsToFloat(c2), x3, y3, Float.intBitsToFloat(c3)});
    }

    public static void end() {
        if (target == null || QUADS.isEmpty()) return;
        var quads = new java.util.ArrayList<>(QUADS);
        QUADS.clear();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] q : quads) for (int k = 0; k < 12; k += 3) {
            minX = Math.min(minX, q[k]);
            maxX = Math.max(maxX, q[k]);
            minY = Math.min(minY, q[k + 1]);
            maxY = Math.max(maxY, q[k + 1]);
        }
        var bounds = new net.minecraft.client.gui.navigation.ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxY - minY) + 1)
                .transformMaxBounds(pose);
        target.guiRenderState.submitGuiElement(new Shapes(quads, new org.joml.Matrix3x2f(pose), bounds));
        target = null;
    }

    private record Shapes(java.util.List<float[]> quads, org.joml.Matrix3x2f pose, net.minecraft.client.gui.navigation.ScreenRectangle bounds)
            implements net.minecraft.client.gui.render.state.GuiElementRenderState {
        @Override
        public void buildVertices(com.mojang.blaze3d.vertex.VertexConsumer vc, float depth) {
            for (float[] q : quads) {
                int[] o = order(q[0], q[1], q[3], q[4], q[6], q[7], q[9], q[10]);
                for (int c : o) vc.addVertexWith2DPose(pose, q[c * 3], q[c * 3 + 1], depth).setColor(Float.floatToRawIntBits(q[c * 3 + 2]));
            }
        }

        @Override
        public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
            return net.minecraft.client.renderer.RenderPipelines.GUI;
        }

        @Override
        public net.minecraft.client.gui.render.TextureSetup textureSetup() {
            return net.minecraft.client.gui.render.TextureSetup.noTexture();
        }

        @Override
        public net.minecraft.client.gui.navigation.ScreenRectangle scissorArea() {
            return null;
        }
    }
    *///?} else if >=1.21.5 {
    /*// the GUI is drawn through render types and a shared buffer source: the strips are written as quads into it
    public static void begin(GuiGraphics g) {
        g.flush();
    }

    public static Matrix4f pose(GuiGraphics g) {
        return g.pose().last().pose();
    }

    public static void strip(Matrix4f m, float cx, float cy, float ra, float rb, float from, float to, int in, int out) {
        if (rb <= ra) return;
        int n = Math.max(3, (int) Math.ceil(208 * (to - from)));
        var buf = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        float px0 = 0, py0 = 0, px1 = 0, py1 = 0;
        for (int i = 0; i <= n; i++) {
            double ang = -Math.PI / 2 - 2 * Math.PI * (from + (to - from) * i / n);
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            float x0 = cx + cos * ra, y0 = cy + sin * ra, x1 = cx + cos * rb, y1 = cy + sin * rb;
            if (i > 0) {
                float[] cx4 = {px0, px1, x1, x0}, cy4 = {py0, py1, y1, y0};
                int[] col4 = {in, out, out, in};
                for (int c : order(px0, py0, px1, py1, x1, y1, x0, y0)) buf.addVertex(m, cx4[c], cy4[c], 0f).setColor(col4[c]);
            }
            px0 = x0; py0 = y0; px1 = x1; py1 = y1;
        }
    }

    public static void end() {
        net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch(net.minecraft.client.renderer.RenderType.gui());
    }
    *///?} else {
    // how many times each strip is drawn: twice, with opposite winding, when face culling is on, otherwise once
    private static int passes = 1;

    public static void begin(GuiGraphics g) {
        passes = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_CULL_FACE) ? 2 : 1;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        ClientCompat.positionColorShader();
    }

    public static Matrix4f pose(GuiGraphics g) {
        return g.pose().last().pose();
    }

    public static void strip(Matrix4f m, float cx, float cy, float ra, float rb, float from, float to, int in, int out) {
        if (rb <= ra) return;
        int n = Math.max(3, (int) Math.ceil(208 * (to - from)));
        for (int pass = 0; pass < passes; pass++) {
            com.mojang.blaze3d.vertex.BufferBuilder buf = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLE_STRIP, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= n; i++) {
                double ang = -Math.PI / 2 - 2 * Math.PI * (from + (to - from) * i / n); // counter-clockwise from the top
                float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
                if (pass == 0) {
                    buf.addVertex(m, cx + cos * ra, cy + sin * ra, 0f).setColor(in);
                    buf.addVertex(m, cx + cos * rb, cy + sin * rb, 0f).setColor(out);
                } else { // same strip, opposite winding
                    buf.addVertex(m, cx + cos * rb, cy + sin * rb, 0f).setColor(out);
                    buf.addVertex(m, cx + cos * ra, cy + sin * ra, 0f).setColor(in);
                }
            }
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buf.buildOrThrow());
        }
    }

    public static void end() {
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    //?}
}
