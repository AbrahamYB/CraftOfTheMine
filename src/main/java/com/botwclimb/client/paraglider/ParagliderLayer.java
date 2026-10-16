package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.botwclimb.common.paraglider.ParagliderData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/** Draws the canopy above the player; it unfolds from the hands while the glider opens. */
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
        if (!p.hasData(BotwClimb.PARAGLIDER)) return;
        ParagliderData g = p.getData(BotwClimb.PARAGLIDER);
        float open = ParagliderAnimator.open(g, partialTick);
        if (open <= 0.02f) return;
        ps.pushPose();
        float sx = Mth.lerp(open, 0.3f, 1f), sy = Mth.lerp(open, 0.15f, 1f);
        ps.translate(0, -10 / 16f, 0); // unfold from the hands
        ps.scale(sx, sy, sx);
        ps.translate(0, 10 / 16f, 0);
        model.renderToBuffer(ps, buffer.getBuffer(model.renderType(ParagliderModel.TEXTURE)), light,
                OverlayTexture.NO_OVERLAY, -1);
        ps.popPose();
    }
}
