package com.botwclimb.client.shrine;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

/** Draws the magic mud on any player who is riding a terminal, so everyone nearby sees it. */
//? if >=1.21.9 {
/*public final class MudHandLayer extends RenderLayer<net.minecraft.client.renderer.entity.state.PlayerRenderState, PlayerModel> {
    public MudHandLayer(RenderLayerParent<net.minecraft.client.renderer.entity.state.PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack ps, net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
                       net.minecraft.client.renderer.entity.state.PlayerRenderState state, float yRot, float xRot) {
        AbstractClientPlayer p = com.botwclimb.client.RenderData.player(state);
        if (p != null) draw(ps, (type, write) -> collector.submitCustomGeometry(ps, type, write::accept), light, p, Mth.clamp(state.ageInTicks - p.tickCount, 0f, 1f), com.botwclimb.client.RenderData.hud(state));
    }
*///?} else if >=1.21.2 {
/*public final class MudHandLayer extends RenderLayer<net.minecraft.client.renderer.entity.state.PlayerRenderState, PlayerModel> {
    public MudHandLayer(RenderLayerParent<net.minecraft.client.renderer.entity.state.PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int light, net.minecraft.client.renderer.entity.state.PlayerRenderState state, float yRot, float xRot) {
        AbstractClientPlayer p = com.botwclimb.client.RenderData.player(state);
        if (p != null) draw(ps, (type, write) -> write.accept(ps.last(), buffer.getBuffer(type)), light, p, Mth.clamp(state.ageInTicks - p.tickCount, 0f, 1f), com.botwclimb.client.RenderData.hud(state));
    }
*///?} else {
public final class MudHandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public MudHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int light, AbstractClientPlayer p, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        draw(ps, (type, write) -> write.accept(ps.last(), buffer.getBuffer(type)), light, p, partialTick, com.botwclimb.client.shrine.totk.TotkRide.drawingHud());
    }
//?}

    /** Where the geometry goes: a write call for the buffer of a render type, however the version hands that out (at once, or later). */
    private interface Out {
        void geom(RenderType type, java.util.function.BiConsumer<PoseStack.Pose, com.mojang.blaze3d.vertex.VertexConsumer> write);
    }

    private static void draw(PoseStack ps, Out out, int light, AbstractClientPlayer p, float partialTick, boolean hud) {
        MudHandClient.State s = MudHandClient.get(p.getId());
        if (s == null) return;
        if (com.botwclimb.client.ClientConfig.style() == com.botwclimb.client.ClientConfig.RideStyle.TOTK) {
            out.geom(com.botwclimb.client.shrine.totk.TotkRide.glowType(), (last, vc) ->
                    com.botwclimb.client.shrine.totk.TotkRide.layer(com.botwclimb.client.ClientCompat.poseStackOf(last), type -> vc, light, p, s, partialTick, hud));
            return;
        }
        MudHandClient.Anim a = MudHandClient.anim(s, p, partialTick, p.level().getGameTime());
        ps.pushPose();
        ps.translate(0, 1.501, 0); // the layer is drawn upside down and mirrored; this puts it back: origin at the feet, y up
        ps.scale(-1, -1, 1);
        out.geom(RenderType.entityCutoutNoCull(MudCocoon.COCOON), (last, vc) -> MudCocoon.cocoon(com.botwclimb.client.ClientCompat.poseStackOf(last), vc, light, a.wrap(), a.stretch()));
        out.geom(RenderType.eyes(MudCocoon.GLOW), (last, vc) -> MudCocoon.cocoon(com.botwclimb.client.ClientCompat.poseStackOf(last), vc, LightTexture.FULL_BRIGHT, a.wrap(), a.stretch())); // the green between the strips shines
        if (a.spill() > 0.001f) {
            float ground = (float) (s.startY() - Mth.lerp(partialTick, p.yo, p.getY()));
            float time = p.level().getGameTime() + partialTick, seed = (s.since() * 37 % 997) * 0.1f; // each ride its own pool
            out.geom(RenderType.entityCutoutNoCull(MudCocoon.COCOON), (last, vc) -> MudCocoon.spill(com.botwclimb.client.ClientCompat.poseStackOf(last), vc, light, a.spill(), ground, time, seed));
            out.geom(RenderType.eyes(MudCocoon.GLOW), (last, vc) -> MudCocoon.spill(com.botwclimb.client.ClientCompat.poseStackOf(last), vc, LightTexture.FULL_BRIGHT, a.spill(), ground, time, seed));
        }
        if (a.speed() > 0.15f) {
            out.geom(RenderType.entityTranslucentEmissive(MudCocoon.STREAK), (last, vc) -> MudCocoon.streaks(com.botwclimb.client.ClientCompat.poseStackOf(last), vc, a.speed(), s.rising()));
        }
        ps.popPose();
    }
}
