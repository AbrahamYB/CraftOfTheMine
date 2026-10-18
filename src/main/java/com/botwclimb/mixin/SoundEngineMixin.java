package com.botwclimb.mixin;

import com.botwclimb.client.bullettime.BulletAudio;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bullet time's slowed sound. The sound engine asks for every playing sound's pitch and volume each tick; this scales
 * both while bullet time runs (see {@link BulletAudio}), so music and ambience that are already playing slow down too.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "calculatePitch(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void botwclimb$pitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValueF() * BulletAudio.pitchFactor(sound));
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void botwclimb$volume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValueF() * BulletAudio.volumeFactor(sound));
    }
}
