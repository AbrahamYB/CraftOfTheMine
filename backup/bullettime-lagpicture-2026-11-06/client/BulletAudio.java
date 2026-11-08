package com.botwclimb.client.bullettime;

import com.botwclimb.BotwClimb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The sound of time slowing down. While bullet time runs, every sound that is playing or starts (music, ambience, mobs,
 * blocks, weather) is pitched down, which also slows it, and turned down, the music most of all. The player's own
 * sounds (the bow, steps) and the user interface stay normal, so aiming stays responsive. It fades in over a few ticks
 * and out over about half a second, matching the picture catching up.
 *
 * The sound engine asks for each playing sound's pitch and volume every tick, so a small mixin
 * ({@code SoundEngineMixin}) multiplies those answers by the factors below.
 * shortcut: Breath of the Wild's exact slow-motion mix is not documented anywhere I could find, so these numbers are a
 * best guess; there is no low-pass muffling and no extra sound effect, only pitch and volume.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class BulletAudio {
    /** Pitch multiplier at full effect: the world, and the music (which keeps a little more of its tone). */
    private static final float WORLD_PITCH = 0.62f, MUSIC_PITCH = 0.78f;
    /** Volume multiplier at full effect. */
    private static final float WORLD_VOLUME = 0.75f, MUSIC_VOLUME = 0.35f;
    /** Sounds this close to the player count as the player's own. */
    private static final double OWN_RADIUS_SQ = 2.25;

    private static float level; // 0 = normal, 1 = full slow-motion sound

    private BulletAudio() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        float target = BulletTime.slowedNow() ? 1f : 0f;
        level += (target - level) * (target > level ? 0.35f : 0.12f); // quick in, a bit slower out
        if (Math.abs(target - level) < 0.01f) level = target;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        level = 0f;
    }

    /** Pitch factor for a sound right now (1 = untouched). Called by the sound engine mixin. */
    public static float pitchFactor(SoundInstance sound) {
        if (level <= 0f || exempt(sound)) return 1f;
        return Mth.lerp(level, 1f, isMusic(sound) ? MUSIC_PITCH : WORLD_PITCH);
    }

    /** Volume factor for a sound right now (1 = untouched). Called by the sound engine mixin. */
    public static float volumeFactor(SoundInstance sound) {
        if (level <= 0f || exempt(sound)) return 1f;
        return Mth.lerp(level, 1f, isMusic(sound) ? MUSIC_VOLUME : WORLD_VOLUME);
    }

    private static boolean isMusic(SoundInstance s) {
        return s.getSource() == SoundSource.MUSIC;
    }

    /** The music is always affected; other interface (relative) sounds and the player's own sounds are not. */
    private static boolean exempt(SoundInstance s) {
        if (isMusic(s)) return false;
        if (s.getSource() == SoundSource.MASTER || s.isRelative()) return true;
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.distanceToSqr(s.getX(), s.getY(), s.getZ()) < OWN_RADIUS_SQ;
    }
}
