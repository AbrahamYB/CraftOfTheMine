package com.botwclimb.client.altar;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * One Sheikah letter drifting up from the altar in a gentle curve, like the enchanting table's glyphs. The letter (0 = A ... 25 = Z)
 * comes in as the x speed of the spawn call, and the caller tints it afterwards, so it has the colour the altar is lit in.
 */
public class SheikahGlyph extends TextureSheetParticle {
    private static final int FADE_IN = 8, FADE_OUT = 14;
    private static final double SHADOW = 0.012, FALL = 0.22;
    private final double bx, by, bz, rise, swayX, swayZ;

    private SheikahGlyph(ClientLevel level, double x, double y, double z, SpriteSet sprites, int letter) {
        super(level, x, y, z);
        setSprite(sprites.get(letter, 25));
        bx = x;
        by = y;
        bz = z;
        rise = 0.55 + random.nextDouble() * 0.35;
        swayX = (random.nextDouble() - 0.5) * 0.3;
        swayZ = (random.nextDouble() - 0.5) * 0.3;
        quadSize = 0.055f;
        lifetime = 40 + random.nextInt(15);
        hasPhysics = false;
        alpha = 0f;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = age / (float) lifetime, e = 1f - (1f - t) * (1f - t); // eased out: quick to leave the altar, slow at the top
        double bend = Math.sin(t * Math.PI);
        x = bx + swayX * bend;
        y = by + rise * e - FALL * t * t * t; // rises, then gravity eases it back down a little at the end
        z = bz + swayZ * bend;
        alpha = Math.min(1f, Math.min(age / (float) FADE_IN, (lifetime - age) / (float) FADE_OUT)) * 1.0f;
    }

    /** A faint dark copy a hair down and to the side, behind the letter, so it lifts off bright backgrounds. */
    @Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTick) {
        float r = rCol, g = gCol, b = bCol, a = alpha;
        double px = x, py = y, pz = z, pxo = xo, pyo = yo, pzo = zo;
        var left = camera.getLeftVector();
        double dx = -left.x() * SHADOW, dy = -SHADOW, dz = -left.z() * SHADOW;
        rCol = gCol = bCol = 0f;
        alpha = a * 0.3f;
        x += dx; y += dy; z += dz; xo += dx; yo += dy; zo += dz;
        super.render(buffer, camera, partialTick);
        rCol = r; gCol = g; bCol = b; alpha = a;
        x = px; y = py; z = pz; xo = pxo; yo = pyo; zo = pzo;
        super.render(buffer, camera, partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 15728880; // glows at full brightness
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new SheikahGlyph(level, x, y, z, sprites, Mth.positiveModulo((int) xd, 26));
        }
    }
}
