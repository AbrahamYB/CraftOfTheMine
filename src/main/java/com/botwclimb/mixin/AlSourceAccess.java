package com.botwclimb.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A channel's OpenAL source id. */
@Mixin(Channel.class)
public interface AlSourceAccess {
    @Accessor("source")
    int botwclimb$source();
}
