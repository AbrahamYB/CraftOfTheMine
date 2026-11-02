package com.botwclimb.client.altar;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.altar.StaminaAltarBlockEntity;
import com.botwclimb.common.climb.ClimbConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws the altar's glowing rings and runes at full brightness on top of the dark block. What is lit comes from the local
 * player's own upgrades (see {@link AltarFx}), so it differs from player to player. Both rings are always there, shadowed
 * until their quarters are unlocked; the runes on the four sides light up from the first upgrade.
 */
//? if >=1.21.11 {
/*public class AltarRenderer implements BlockEntityRenderer<StaminaAltarBlockEntity, AltarRenderer.State> {
    public static class State extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState {
        float partial;
    }
*///?} else {
public class AltarRenderer implements BlockEntityRenderer<StaminaAltarBlockEntity> {
//?}

    /** Where the geometry goes: the buffer of a render type (a place to write vertices), however the version hands that out. */
    private interface Out {
        void geom(RenderType type, java.util.function.BiConsumer<PoseStack.Pose, VertexConsumer> write);
    }

    private static final float TOP = 12f / 16f + 0.02f, SIDE = 0.02f; // far enough off the surface not to z-fight with it
    /** Ring radii in blocks: the outer ring is the first extra wheel, the inner one the second. */
    private static final float[][] RINGS = {{0.26f, 0.355f}, {0.125f, 0.195f}};
    private static int gridCache;

    /** Texels per block: the width of the altar's top texture (128 for the detailed version, 16 for the vanilla-sized one), so the rings sit on the stone's own pixel grid. */
    private static int grid() {
        if (gridCache == 0) {
            try (var in = Minecraft.getInstance().getResourceManager().open(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/block/stamina_altar_top.png"));
                 var img = com.mojang.blaze3d.platform.NativeImage.read(in)) {
                gridCache = img.getWidth();
            } catch (Exception e) {
                gridCache = 128;
            }
        }
        return gridCache;
    }
    /** The rune: cells (row from the top, column) on a 16 x 12 face. */
    private static final int[][] RUNE = {{5, 5}, {5, 6}, {6, 6}, {7, 6}, {7, 7}, {5, 9}, {6, 9}, {7, 9}, {6, 10}, {5, 10}, {6, 8}};

    public AltarRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {}

    //? if >=1.21.11 {
    /*@Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StaminaAltarBlockEntity be, State state, float partialTick, net.minecraft.world.phys.Vec3 camera,
                                   net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState.extractBase(be, state, crumbling);
        state.partial = partialTick;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && p.hasData(BotwClimb.STAMINA_UPGRADES)) {
            int upgrades = AltarFx.upgrades(p);
            glyphs(be, upgrades, AltarFx.lit(upgrades, partialTick)); // the drifting letters are spawned as the altar is looked at, as before
        }
    }

    @Override
    public void submit(State state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.CameraRenderState camera) {
        draw((type, write) -> collector.submitCustomGeometry(pose, type, write::accept), pose, state.partial, null);
    }
    *///?} else {
    @Override
    public void render(StaminaAltarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        draw((type, write) -> write.accept(pose.last(), buffers.getBuffer(type)), pose, partialTick, be);
    }
    //?}

    /** All the glowing rings and runes (and, when a block entity is given, the drifting letters). */
    private static void draw(Out out, PoseStack pose, float partialTick, StaminaAltarBlockEntity be) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !p.hasData(BotwClimb.STAMINA_UPGRADES)) return;
        int upgrades = AltarFx.upgrades(p);
        AltarFx.Look lit = AltarFx.lit(upgrades, partialTick);
        boolean dark = AltarFx.finaleActive() && lit.level() <= 0f; // all the way off: nothing is drawn
        if (dark) return;
        boolean maxed = upgrades >= ClimbConstants.STAMINA_MAX_UPGRADES;
        float shadowFade = AltarFx.finaleActive() ? lit.level() : 1f; // the shadows go out with the lights during the finale
        for (int ring = 0; ring < 2; ring++) {
            for (int q = 0; q < 4; q++) {
                boolean on = maxed || AltarFx.quarterLit(upgrades, ring, q);
                int rgb = on ? lit.rgb() : AltarFx.SHADOW;
                float a = on ? lit.level() : 0.9f * shadowFade;
                if (a <= 0.01f) continue;
                arcQuad(out, ring, q, on, rgb, a);
            }
        }
        if (be != null) glyphs(be, upgrades, lit);
        if (upgrades >= 1 || AltarFx.finaleActive()) {
            if (lit.level() > 0.01f) runes(out, lit.rgb(), lit.level(), true);
        } else {
            runes(out, AltarFx.SHADOW, 0.9f, false);
        }
    }

    /** One Sheikah letter at a time, a new one every few ticks, each a different random letter in the colour the altar is lit in. */
    private static void glyphs(StaminaAltarBlockEntity be, int upgrades, AltarFx.Look lit) {
        var level = be.getLevel();
        if (level == null || lit.level() < 0.5f) return;
        long now = level.getGameTime();
        int quarters = upgrades >= ClimbConstants.STAMINA_MAX_UPGRADES ? 8 : Mth.clamp(upgrades, 0, 8);
        if (quarters == 0 || now < be.lastParticleTick + 14 - quarters) return; // spaced out: they never come in bursts
        var rnd = level.getRandom();
        be.lastParticleTick = now + rnd.nextInt(4);
        int pick = rnd.nextInt(quarters), ring = pick / 4, q = pick % 4; // above one of the lit quarters
        float[] s = pos(Mth.lerp(rnd.nextFloat(), RINGS[ring][0], RINGS[ring][1]), q * 90f + 8f + rnd.nextFloat() * 74f);
        var pos = be.getBlockPos();
        var particle = Minecraft.getInstance().particleEngine.createParticle(BotwClimb.GLYPH.get(),
                pos.getX() + s[0], pos.getY() + TOP + 0.15, pos.getZ() + s[1], rnd.nextInt(26), 0, 0);
        if (particle instanceof net.minecraft.client.particle.TextureSheetParticle sq) sq.setColor((lit.rgb() >> 16 & 255) / 255f, (lit.rgb() >> 8 & 255) / 255f, (lit.rgb() & 255) / 255f);
    }

    /**
     * A ring quarter on the top face. The 128x pattern of cells (same pixel grid as the stone, mottled per cell) is baked once
     * into a texture per ring and look, so a quarter is a single quad tinted by the current colour instead of thousands of cells.
     */
    private static void arcQuad(Out out, int ring, int q, boolean lit, int rgb, float a) {
        final int GRID = grid();
        ResourceLocation tex = TOP_TEX[ring][lit ? 1 : 0];
        if (tex == null) tex = TOP_TEX[ring][lit ? 1 : 0] = bake("top" + ring + (lit ? "l" : "s"), GRID, GRID, (img, l) -> {
            for (int qq = 0; qq < 4; qq++) {
                float r0 = RINGS[ring][0], r1 = RINGS[ring][1];
                for (int ix = 0; ix < GRID; ix++) {
                    for (int iz = 0; iz < GRID; iz++) {
                        float dx = (ix + 0.5f) / GRID - 0.5f, dz = (iz + 0.5f) / GRID - 0.5f;
                        float rad = Mth.sqrt(dx * dx + dz * dz);
                        if (rad < r0 || rad > r1) continue;
                        float deg = (float) Math.toDegrees(Math.atan2(-dx, -dz));
                        if (deg < 0) deg += 360f;
                        if (deg < qq * 90f + 2f || deg > (qq + 1) * 90f - 2f) continue;
                        com.botwclimb.client.ClientCompat.setGrey(img, ix, iz, grey(tone(0xFFFFFF, ix, iz, ring * 4 + qq, l)));
                    }
                }
            }
        }, lit);
        // quarter q covers one quadrant of the block (counter-clockwise from north-west), so draw just that half-by-half square
        float x0 = (q == 0 || q == 1) ? 0f : 0.5f, z0 = (q == 0 || q == 3) ? 0f : 0.5f;
        final float fx0 = x0, fz0 = z0;
        out.geom(RenderType.entityTranslucentEmissive(tex), (last, vc) ->
                quadUV(vc, last, fx0, TOP, fz0, fx0 + 0.5f, TOP, fz0, fx0 + 0.5f, TOP, fz0 + 0.5f, fx0, TOP, fz0 + 0.5f, fx0, fz0, fx0 + 0.5f, fz0, fx0 + 0.5f, fz0 + 0.5f, fx0, fz0 + 0.5f, rgb, a, 0, 1, 0));
    }

    private static final ResourceLocation[][] TOP_TEX = new ResourceLocation[2][2];
    private static final ResourceLocation[][] RUNE_TEX = new ResourceLocation[4][2];

    private interface Painter { void paint(com.mojang.blaze3d.platform.NativeImage img, boolean lit); }

    private static ResourceLocation bake(String name, int w, int h, Painter painter, boolean lit) {
        var img = new com.mojang.blaze3d.platform.NativeImage(w, h, true); // starts fully transparent
        painter.paint(img, lit);
        var id = ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "altar_" + name);
        //? if >=1.21.11 {
        /*Minecraft.getInstance().getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> id.toString(), img));
        *///?} else {
        Minecraft.getInstance().getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(img));
        //?}
        return id;
    }

    private static int grey(int rgb) {
        return 0xFF000000 | (rgb & 255) << 16 | (rgb & 255) << 8 | (rgb & 255); // the tone of a white base is the same in every channel
    }

    /** One cell of a shadowed part: the shadow colour scaled by a soft blotchy pattern plus per-cell grain. */
    private static int tone(int rgb, int x, int y, int seed, boolean lit) {
        float gx = x / (24f * grid() / 128f), gy = y / (24f * grid() / 128f); // the blotch is the same size in the world whatever the texture size
        int ix = (int) Math.floor(gx), iy = (int) Math.floor(gy);
        float fx = (float) Mth.smoothstep(gx - ix), fy = (float) Mth.smoothstep(gy - iy);
        float blotch = Mth.lerp(fy, Mth.lerp(fx, hash(ix, iy, seed), hash(ix + 1, iy, seed)), Mth.lerp(fx, hash(ix, iy + 1, seed), hash(ix + 1, iy + 1, seed)));
        float f = (lit ? 0.66f : 0.90f) + (lit ? 0.28f : 0.10f) * blotch + (lit ? 0.06f : 0.04f) * hash(x, y, seed + 91); // a calm surface: only a slight variation (a lit colour is already at full brightness, so it varies downwards)
        int rr = Math.min(255, Math.round((rgb >> 16 & 255) * f)), gg = Math.min(255, Math.round((rgb >> 8 & 255) * f)), bb = Math.min(255, Math.round((rgb & 255) * f));
        return rr << 16 | gg << 8 | bb;
    }

    private static float hash(int x, int y, int s) {
        int h = x * 374761393 + y * 668265263 + s * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    private static float[] pos(float r, float deg) {
        double t = Math.toRadians(deg);
        return new float[]{0.5f - (float) Math.sin(t) * r, 0.5f - (float) Math.cos(t) * r};
    }

    /** The rune on each of the four sides, one baked texture (8 pieces per 1/16 cell, mottled like the rings) and one quad per side. */
    private static void runes(Out out, int rgb, float a, boolean lit) {
        float top = 12f / 16f, o = SIDE;
        for (int side = 0; side < 4; side++) {
            int sd = side + 1;
            ResourceLocation tex = RUNE_TEX[side][lit ? 1 : 0];
            if (tex == null) tex = RUNE_TEX[side][lit ? 1 : 0] = bake("rune" + side + (lit ? "l" : "s"), grid(), grid() * 3 / 4, (img, l) -> {
                for (int[] cell : RUNE)
                    for (int i = 0; i < grid() / 16; i++)
                        for (int j = 0; j < grid() / 16; j++) {
                            int gu = cell[1] * (grid() / 16) + i, gv = cell[0] * (grid() / 16) + j;
                            com.botwclimb.client.ClientCompat.setGrey(img, gu, gv, grey(tone(0xFFFFFF, gu, gv, sd, l)));
                        }
            }, lit);
            final int fs = side;
            out.geom(RenderType.entityTranslucentEmissive(tex), (last, vc) -> {
                // north (z = 0), south (z = 1), west (x = 0), east (x = 1): u runs left to right as seen from outside
                switch (fs) {
                    case 0 -> quadUV(vc, last, 1, top, -o, 0, top, -o, 0, 0, -o, 1, 0, -o, 0, 0, 1, 0, 1, 1, 0, 1, rgb, a, 0, 0, -1);
                    case 1 -> quadUV(vc, last, 0, top, 1 + o, 1, top, 1 + o, 1, 0, 1 + o, 0, 0, 1 + o, 0, 0, 1, 0, 1, 1, 0, 1, rgb, a, 0, 0, 1);
                    case 2 -> quadUV(vc, last, -o, top, 0, -o, top, 1, -o, 0, 1, -o, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, rgb, a, -1, 0, 0);
                    default -> quadUV(vc, last, 1 + o, top, 1, 1 + o, top, 0, 1 + o, 0, 0, 1 + o, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, rgb, a, 1, 0, 0);
                }
            });
        }
    }

    /** A textured quad tinted by rgb; for the top face the uv is the position (u = x, v = z), for the sides u runs along the face and v down from its top. */
    private static void quadUV(VertexConsumer vc, PoseStack.Pose last,
                               float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                               float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3, int rgb, float a, float nx, float ny, float nz) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float[][] p = {{x0, y0, z0, u0, v0}, {x1, y1, z1, u1, v1}, {x2, y2, z2, u2, v2}, {x3, y3, z3, u3, v3}};
        for (float[] c : p)
            vc.addVertex(last.pose(), c[0], c[1], c[2]).setColor(r, g, b, a).setUv(c[3], c[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(last, nx, ny, nz);
    }

    //? if <1.21.11 {
    @Override
    public boolean shouldRenderOffScreen(StaminaAltarBlockEntity be) {
        return false;
    }
    //?}
}
