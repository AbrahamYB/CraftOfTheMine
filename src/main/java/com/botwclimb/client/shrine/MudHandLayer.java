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
public final class MudHandLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public MudHandLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffer, int light, AbstractClientPlayer p, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        MudHandClient.State s = MudHandClient.get(p.getId());
        if (s == null) return;
        if (com.botwclimb.client.ClientConfig.style() == com.botwclimb.client.ClientConfig.RideStyle.TOTK) {
            com.botwclimb.client.shrine.totk.TotkRide.layer(ps, buffer, light, p, s, partialTick);
            return;
        }
        MudHandClient.Anim a = MudHandClient.anim(s, p, partialTick, p.level().getGameTime());
        ps.pushPose();
        ps.translate(0, 1.501, 0); // the layer is drawn upside down and mirrored; this puts it back: origin at the feet, y up
        ps.scale(-1, -1, 1);
        MudCocoon.cocoon(ps, buffer.getBuffer(RenderType.entityCutoutNoCull(MudCocoon.COCOON)), light, a.wrap(), a.stretch());
        MudCocoon.cocoon(ps, buffer.getBuffer(RenderType.eyes(MudCocoon.GLOW)), LightTexture.FULL_BRIGHT, a.wrap(), a.stretch()); // the green between the strips shines
        if (a.spill() > 0.001f) {
            float ground = (float) (s.startY() - Mth.lerp(partialTick, p.yo, p.getY()));
            float time = p.level().getGameTime() + partialTick, seed = (s.since() * 37 % 997) * 0.1f; // each ride its own pool
            MudCocoon.spill(ps, buffer.getBuffer(RenderType.entityCutoutNoCull(MudCocoon.COCOON)), light, a.spill(), ground, time, seed);
            MudCocoon.spill(ps, buffer.getBuffer(RenderType.eyes(MudCocoon.GLOW)), LightTexture.FULL_BRIGHT, a.spill(), ground, time, seed);
        }
        if (a.speed() > 0.15f) {
            MudCocoon.streaks(ps, buffer.getBuffer(RenderType.entityTranslucentEmissive(MudCocoon.STREAK)), a.speed(), s.rising());
        }
        ps.popPose();
    }
}
