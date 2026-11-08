package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.paraglider.FabricDefinition;
import com.botwclimb.common.paraglider.FabricRegistry;
import com.botwclimb.common.paraglider.ParagliderData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Draws the canopy above the player; it unfolds from the hands while the glider opens. */
//? if >=1.21.9 {
/*public final class ParagliderLayer extends RenderLayer<net.minecraft.client.renderer.entity.state.PlayerRenderState, PlayerModel> {
    private final ParagliderModel model;

    public ParagliderLayer(RenderLayerParent<net.minecraft.client.renderer.entity.state.PlayerRenderState, PlayerModel> parent, ParagliderModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void submit(PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
                       net.minecraft.client.renderer.entity.state.PlayerRenderState state, float yRot, float xRot) {
        AbstractClientPlayer p = com.botwclimb.client.RenderData.player(state);
        if (p != null) draw(model, ps, (type, write) -> collector.submitCustomGeometry(ps, type, write::accept), light, p, Mth.clamp(state.ageInTicks - p.tickCount, 0f, 1f));
    }
*///?} else if >=1.21.2 {
/*public final class ParagliderLayer extends RenderLayer<net.minecraft.client.renderer.entity.state.PlayerRenderState, PlayerModel> {
    private final ParagliderModel model;

    public ParagliderLayer(RenderLayerParent<net.minecraft.client.renderer.entity.state.PlayerRenderState, PlayerModel> parent, ParagliderModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int light, net.minecraft.client.renderer.entity.state.PlayerRenderState state, float yRot, float xRot) {
        AbstractClientPlayer p = com.botwclimb.client.RenderData.player(state);
        if (p != null) draw(model, ps, (type, write) -> write.accept(ps.last(), buffer.getBuffer(type)), light, p, Mth.clamp(state.ageInTicks - p.tickCount, 0f, 1f));
    }
*///?} else {
public final class ParagliderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ParagliderModel model;

    public ParagliderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                           ParagliderModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int light, AbstractClientPlayer p, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        draw(model, ps, (type, write) -> write.accept(ps.last(), buffer.getBuffer(type)), light, p, partialTick);
    }
//?}

    /** Where the geometry goes: a write call for the buffer of a render type, however the version hands that out (at once, or later). */
    private interface Out {
        void geom(RenderType type, java.util.function.BiConsumer<PoseStack.Pose, VertexConsumer> write);
    }

    private static void draw(ParagliderModel model, PoseStack ps, Out out, int light, AbstractClientPlayer p, float partialTick) {
        if (!p.hasData(BotwClimb.PARAGLIDER)) return;
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        float open = ParagliderAnimator.open(g, partialTick);
        if (open <= 0.02f) return;
        ps.pushPose();
        float sx = Mth.lerp(open, 0.3f, 1f), sy = Mth.lerp(open, 0.15f, 1f);
        ps.translate(0, -10 / 16f, 0); // unfold from the hands
        ps.scale(sx, sy, sx);
        ps.translate(0, 10 / 16f, 0);
        // what the model is posed with, taken now: from 1.21.11 it is drawn after every player has been described
        float windF = Mth.lerp(partialTick, g.windFO, g.windF), windR = Mth.lerp(partialTick, g.windRO, g.windR), time = p.tickCount + partialTick;
        float gust = g.gust, rocket = Mth.lerp(partialTick, g.rocketGustO, g.rocketGust);
        FabricDefinition fabric = FabricRegistry.byIndex(g.fabric);
        boolean ribbons = fabric.hasLongRibbons();
        out.geom(model.renderType(fabric.texture()), (last, vc) -> {
            model.flutter(windF, windR, time, gust, rocket);
            model.setRibbons(ribbons);
            PoseStack own = com.botwclimb.client.ClientCompat.poseStackOf(last);
            model.renderToBuffer(own, vc, light, OverlayTexture.NO_OVERLAY, -1);
            model.renderShade(own, vc, light);
        });
        ps.popPose();
    }
}
