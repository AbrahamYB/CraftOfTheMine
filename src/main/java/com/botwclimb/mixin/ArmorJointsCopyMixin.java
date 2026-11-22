package com.botwclimb.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** When the armor layer copies the player's pose onto the armor, the elbows and knees go with it (see ArmorJointsMixin). Only before 1.21.2. */
@Mixin(net.minecraft.client.model.HumanoidModel.class)
public abstract class ArmorJointsCopyMixin {
    //? if <1.21.2 {
    @org.spongepowered.asm.mixin.injection.Inject(method = "copyPropertiesTo", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void botwclimb$joints(net.minecraft.client.model.HumanoidModel<?> to, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.botwclimb.client.PlayerJoints.copyJoints((net.minecraft.client.model.HumanoidModel<?>) (Object) this, to);
    }
    //?}
}
