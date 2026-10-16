package com.botwclimb.client.paraglider;

import com.botwclimb.BotwClimb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Original canopy design: a wide, slightly drooping wing with a thick leading edge and two struts down to the hands.
 * Coordinates are in player-model pixels (y down, shoulders at y = 0, hands raised to about y = -10).
 */
public final class ParagliderModel extends Model {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "paraglider"), "main");
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/entity/paraglider.png");

    private final ModelPart root;

    public ParagliderModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition canopy = root.addOrReplaceChild("canopy", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-8, -1, -8, 16, 1, 16)
                        .texOffs(0, 17).addBox(-8, -2, -10, 16, 2, 2),
                PartPose.offset(0, -22, 0));
        PartDefinition left = canopy.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(0, 0).addBox(0, -1, -8, 8, 1, 16), PartPose.offsetAndRotation(8, 0, 0, 0, 0, 0.22f));
        left.addOrReplaceChild("tip_l", CubeListBuilder.create()
                .texOffs(0, 0).addBox(0, -1, -7, 7, 1, 14), PartPose.offsetAndRotation(8, 0, 0, 0, 0, 0.25f));
        PartDefinition right = canopy.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -1, -8, 8, 1, 16), PartPose.offsetAndRotation(-8, 0, 0, 0, 0, -0.22f));
        right.addOrReplaceChild("tip_r", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7, -1, -7, 7, 1, 14), PartPose.offsetAndRotation(-8, 0, 0, 0, 0, -0.25f));
        root.addOrReplaceChild("strut_l", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-0.5f, 0, -0.5f, 1, 13, 1), PartPose.offset(5, -22, -1));
        root.addOrReplaceChild("strut_r", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-0.5f, 0, -0.5f, 1, 13, 1), PartPose.offset(-5, -22, -1));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
