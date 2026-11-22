package com.botwclimb.mixin;

import com.botwclimb.client.PlayerJoints;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right after the player model's mesh is built, gives the arms and legs their elbows and knees (see {@link PlayerJoints}). */
@Mixin(PlayerModel.class)
public abstract class PlayerJointsMixin {
    //? if <1.21.9 {
    @Inject(method = "createMesh", at = @At("RETURN"))
    private static void botwclimb$joints(CubeDeformation deformation, boolean slim, CallbackInfoReturnable<MeshDefinition> cir) {
        PlayerJoints.split(cir.getReturnValue().getRoot(), deformation, slim);
    }
    //?}
}
