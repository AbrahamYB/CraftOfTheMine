package com.botwclimb.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives the armor model elbows and knees and makes it bend them as the player's model does, so the ride's bent knees bend the leggings and
 * boots too (see {@link com.botwclimb.client.PlayerJoints#splitArmor}). Only before 1.21.2.
 */
//? if <1.21.2 {
@Mixin(net.minecraft.client.model.HumanoidArmorModel.class)
public abstract class ArmorJointsMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "createBodyLayer", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private static void botwclimb$joints(net.minecraft.client.model.geom.builders.CubeDeformation deformation,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.model.geom.builders.MeshDefinition> cir) {
        com.botwclimb.client.PlayerJoints.splitArmor(cir.getReturnValue().getRoot(), deformation);
    }
}
//?} else {
/*@Mixin(net.minecraft.client.model.HumanoidModel.class)
public abstract class ArmorJointsMixin {
}
*///?}
