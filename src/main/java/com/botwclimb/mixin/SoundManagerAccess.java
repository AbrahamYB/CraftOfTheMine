package com.botwclimb.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches the sound engine, to find the OpenAL source of the rider's ride sound (see TotkRide.hear). */
@Mixin(SoundManager.class)
public interface SoundManagerAccess {
    @Accessor("soundEngine")
    SoundEngine botwclimb$engine();
}
