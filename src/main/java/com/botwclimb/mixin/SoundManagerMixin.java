package com.botwclimb.mixin;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Changing world stops every sound. The terminal ride changes world in the middle of the void, so while the rider's ride sound
 * plays, everything else is stopped as usual but the Players category (where the ride sound is) is left to finish.
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "stop()V", at = @At("HEAD"), cancellable = true)
    private void botwclimb$keepRideSound(CallbackInfo ci) {
        if (!com.botwclimb.client.shrine.totk.TotkRide.riderSoundPlaying()) return;
        for (SoundSource s : SoundSource.values()) if (s != SoundSource.PLAYERS) ((SoundManager) (Object) this).stop(null, s);
        ci.cancel();
    }
}
