package com.botwclimb.mixin;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.ClimbAnimator;
import com.botwclimb.client.paraglider.ParagliderAnimator;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla offers no hook between "pose the limbs" and "draw the model", so this is the one small mixin: it runs at
 * the end of PlayerModel.setupAnim and lets {@link ClimbAnimator} bend the pose.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    //? if >=1.21.2 {
    /*@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void botwclimb$pose(net.minecraft.client.renderer.entity.state.PlayerRenderState state, CallbackInfo ci) {
        AbstractClientPlayer p = com.botwclimb.client.RenderData.player(state);
        if (p == null) return;
        // ageInTicks = tickCount + partialTick; first-person hand rendering passes 0, which we must not pose.
        float ageInTicks = state.ageInTicks, netHeadYaw = state.yRot;
        boolean hud = com.botwclimb.client.RenderData.hud(state);
        *///?} else {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void botwclimb$pose(net.minecraft.world.entity.LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer p)) return;
        // ageInTicks = tickCount + partialTick; first-person hand rendering passes 0, which we must not pose.
        boolean hud = com.botwclimb.client.shrine.totk.TotkRide.drawingHud();
        //?}
        float pt = ageInTicks - p.tickCount;
        if (pt < 0f || pt > 1.001f) return;
        if (p.hasData(BotwClimb.CLIMB)) {
            ClimbAnimator.apply((PlayerModel) (Object) this, p.getData(BotwClimb.CLIMB), pt, netHeadYaw);
        }
        if (p.hasData(BotwClimb.PARAGLIDER)) {
            ParagliderAnimator.apply((PlayerModel) (Object) this, p.getData(BotwClimb.PARAGLIDER), pt, ageInTicks, netHeadYaw);
        }
        com.botwclimb.client.shrine.totk.TotkRide.pose((PlayerModel) (Object) this, p, pt, hud);
    }
}
