package com.botwclimb.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;

/** A place to write the vertices of a render type: the game's own buffer source up to 26.1, which 26.2 removed. */
@FunctionalInterface
public interface Buffers {
    VertexConsumer getBuffer(RenderType type);

    /** Draws what was written to this render type (a no-op for sources that draw on their own). */
    default void endBatch(RenderType type) {}
}
