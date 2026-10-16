package com.botwclimb.common.paraglider;

import com.botwclimb.BotwClimb;
import net.minecraft.resources.ResourceLocation;

/**
 * One paraglider fabric. All fabrics share the same model and UV layout; a fabric is just a texture
 * (assets/botwclimb/textures/entity/paraglider/ID.png, drawn by tools/make_fabrics.py, which also bakes in the
 * tassel, ribbon and trim colours) plus whether the long trailing ribbons are drawn.
 */
public record FabricDefinition(String id, boolean hasLongRibbons) {
    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(BotwClimb.MOD_ID, "textures/entity/paraglider/" + id + ".png");
    }
}
