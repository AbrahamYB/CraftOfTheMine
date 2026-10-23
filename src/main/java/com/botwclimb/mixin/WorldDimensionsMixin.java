package com.botwclimb.mixin;

import com.botwclimb.common.shrine.PlotWorld;
import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft calls a world "experimental" (and asks for a backup when it is opened or created) as soon as it has any dimension
 * besides the three vanilla ones. The plot dimension is the only extra one here and nothing about it is unstable, so a world
 * whose dimensions are the vanilla three plus the plots counts as stable again.
 */
@Mixin(WorldDimensions.Complete.class)
public abstract class WorldDimensionsMixin {
    @Inject(method = "lifecycle", at = @At("RETURN"), cancellable = true)
    private void botwclimb$plotsAreStable(CallbackInfoReturnable<Lifecycle> cir) {
        var dimensions = ((WorldDimensions.Complete) (Object) this).dimensions();
        for (var key : dimensions.registryKeySet()) {
            if (key != LevelStem.OVERWORLD && key != LevelStem.NETHER && key != LevelStem.END && !key.location().equals(PlotWorld.DIM.location())) return;
        }
        cir.setReturnValue(Lifecycle.stable());
    }
}
