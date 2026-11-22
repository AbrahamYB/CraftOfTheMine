package com.botwclimb.mixin;

import org.spongepowered.asm.mixin.Mixin;

/** While the void's rider is drawn, the light on them comes from the spiral round them, not the inventory's fixed lamp (see TotkRide#hudLight). */
@Mixin(com.mojang.blaze3d.platform.Lighting.class)
public abstract class HudLightMixin {
    //? if <1.21.6 {
    @org.spongepowered.asm.mixin.injection.Inject(method = "setupForEntityInInventory()V", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private static void botwclimb$spiralLight(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        org.joml.Vector3f[] l = com.botwclimb.client.shrine.totk.TotkRide.hudLight();
        if (l == null) return;
        com.mojang.blaze3d.systems.RenderSystem.setShaderLights(l[0], l[1]);
        ci.cancel();
    }
    //?}
}
