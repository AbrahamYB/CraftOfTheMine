package com.botwclimb.mixin;

import com.botwclimb.common.bullettime.BulletConstants;
import com.botwclimb.common.bullettime.BulletSlow;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * A mob's path times out against the game clock, but a slowed mob only gets one tick in every few, so it would decide it is stuck
 * and drop its path long before it could walk it. While bullet time slows it, the allowance is stretched by the same factor.
 */
@Mixin(PathNavigation.class)
public abstract class SlowedPathMixin {
    @Shadow
    @Final
    protected Mob mob;

    @ModifyConstant(require = 0, method = "doStuckDetection", constant = @Constant(doubleValue = 3.0))
    private double botwclimb$stretchTimeout(double budget) {
        return BulletSlow.isSlowed(this.mob, this.mob.level().getGameTime()) ? budget * BulletConstants.PERIOD : budget;
    }
}
