package com.botwclimb.client.shrine;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Really opens the ground for a Return ride: on this client only, the blocks under the rider crack, chip and crumble away into a
 * rough-edged shaft (cracks spread outward from the middle, blocks fall apart in a ragged order, debris keeps falling into the
 * opening), and as the cocoon sinks the shaft closes again from its edges inward. The server never knows; nobody else's world
 * changes. A world change (the ride's trip) throws the record away, since the old world is gone.
 */
@EventBusSubscriber(modid = BotwClimb.MOD_ID, value = Dist.CLIENT)
public final class GroundHole {
    /** How deep the opening goes below the ground the ride stands on, in blocks. */
    private static final int DEPTH = 22;
    private static final int FLAGS = 2 | 16; // show it, but start no neighbour updates
    /** Breaker ids of the crack pictures (one per cracking block); far above any real entity id. */
    private static final int CRACK_ID = 0x5B000000;
    /** The crumbling: a block begins to crack CRACK_TICKS before it breaks; the first break is at FIRST, the middle breaks first and the rim last. */
    private static final float CRACK_TICKS = 11f, FIRST = 5f, SPREAD = 13f, PER_BLOCK_DOWN = 0.45f;

    private static final class Carved {
        final ClientLevel level;
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        /** Block -> {breaker id, crack stage shown}. */
        final Map<BlockPos, int[]> cracks = new HashMap<>();
        int nextId;

        Carved(ClientLevel level) {
            this.level = level;
        }
    }

    private static final Map<Integer, Carved> OPEN = new HashMap<>();

    private GroundHole() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            OPEN.clear();
            return;
        }
        if (ClientConfig.style() == ClientConfig.RideStyle.TOTK) { // that style does its own thing with the ground
            OPEN.values().forEach(c -> giveBack(mc, c));
            OPEN.clear();
            com.botwclimb.client.shrine.totk.TotkRide.tick(mc);
            return;
        }
        boolean allowed = ClientConfig.groundOpening();
        Iterator<Map.Entry<Integer, Carved>> it = OPEN.entrySet().iterator();
        while (it.hasNext()) { // rides that ended (or whose world is gone) give the ground back
            var e = it.next();
            if (e.getValue().level != level) {
                clearCracks(mc, e.getValue());
                it.remove();
            } else if (MudHandClient.get(e.getKey()) == null || !allowed) {
                giveBack(mc, e.getValue());
                it.remove();
            }
        }
        if (!allowed) return;
        for (var ride : MudHandClient.all().entrySet()) {
            Entity rider = level.getEntity(ride.getKey());
            if (rider == null) continue;
            MudHandClient.State s = ride.getValue();
            if (s.kind() != 1) continue; // only a Return opens the ground
            float hole = MudHandClient.anim(s, rider, 0f, level.getGameTime()).hole();
            Carved carved = OPEN.get(ride.getKey());
            if (carved == null) {
                if (s.phase() != 1 && hole < 0.02f) continue;
                carved = new Carved(level);
                OPEN.put(ride.getKey(), carved);
            }
            crumble(mc, level, s, carved, rider, hole);
        }
    }

    /**
     * One tick of the shaft. While the cocoon gathers (phase 1) the shaft is its full width and each block has its own time to
     * crack and to break; afterwards the shaft's width follows {@code hole} as it closes and nothing new breaks.
     */
    private static void crumble(Minecraft mc, ClientLevel level, MudHandClient.State s, Carved carved, Entity rider, float hole) {
        boolean opening = s.phase() == 1;
        float t = level.getGameTime() - s.since();
        int seed = (int) s.since();
        double full = MudCocoon.PIT_RADIUS * 0.9, r = opening ? full : MudCocoon.pitRadius(hole) * 0.9;
        double cx = rider.getX(), cz = rider.getZ();
        int top = (int) Math.floor(s.startY() - 0.001); // the block the rider stands on
        carved.blocks.entrySet().removeIf(e -> { // what the shaft no longer reaches goes back
            BlockPos p = e.getKey();
            if (inside(p.getX(), p.getZ(), cx, cz, r, seed)) return false;
            put(level, p, e.getValue());
            return true;
        });
        int reach = (int) Math.ceil(full * 1.25);
        boolean broke = false;
        for (int x = (int) Math.floor(cx) - reach; x <= (int) Math.floor(cx) + reach; x++) {
            for (int z = (int) Math.floor(cz) - reach; z <= (int) Math.floor(cz) + reach; z++) {
                if (!inside(x, z, cx, cz, r, seed) || !level.hasChunkAt(new BlockPos(x, top, z))) continue;
                double dist = Math.sqrt((x + 0.5 - cx) * (x + 0.5 - cx) + (z + 0.5 - cz) * (z + 0.5 - cz));
                for (int y = top; y > top - DEPTH && y >= com.botwclimb.common.Compat.minY(level); y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (carved.blocks.containsKey(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0) continue;
                    if (opening) {
                        float breakAt = FIRST + SPREAD * (0.55f * (float) (dist / full) + 0.45f * rnd(x, y, z, seed)) + PER_BLOCK_DOWN * (top - y);
                        if (t < breakAt) { // not yet: the top layers show the cracks creeping in
                            if (y >= top - 2) showCrack(mc, carved, pos, Mth.clamp((int) ((t - (breakAt - CRACK_TICKS)) / CRACK_TICKS * 10f), -1, 9));
                            continue;
                        }
                        // down it goes, with a puff of pieces (only some of the blocks, to spare the particle count)
                        if (y >= top - 1 ? rnd(x, y, z, seed + 1) < 0.5f : rnd(x, y, z, seed + 1) < 0.1f) com.botwclimb.client.ClientCompat.destroyEffect(mc, pos, state);
                        broke = true;
                    }
                    showCrack(mc, carved, pos, -1);
                    carved.blocks.put(pos, state);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
        }
        if (broke) level.playLocalSound(cx, top, cz, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.9f, 0.6f + 0.3f * level.random.nextFloat(), false);
        // loose pieces keep falling into the opening, from the first break until the cocoon has gone down
        if (!carved.blocks.isEmpty() && (opening || hole > 0.2f)) {
            for (int i = 0; i < 3; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, d = Math.sqrt(level.random.nextDouble()) * r * 0.9;
                BlockState piece = carved.blocks.values().stream().skip(level.random.nextInt(carved.blocks.size())).findFirst().get();
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, piece), cx + Math.cos(a) * d, top - 0.2, cz + Math.sin(a) * d, 0, -0.15, 0);
            }
        }
    }

    /** Whether the column at x, z is part of the shaft of radius r: its rim is ragged, differently each ride. */
    private static boolean inside(int x, int z, double cx, double cz, double r, int seed) {
        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
        double edge = r * (0.78 + 0.44 * rnd(x, 0, z, seed + 7)); // each column's own reach
        return dx * dx + dz * dz <= edge * edge;
    }

    /** A steady pseudo-random number in 0..1 for a block and a ride. */
    private static float rnd(int x, int y, int z, int seed) {
        int h = x * 73856093 ^ y * 83492791 ^ z * 19349663 ^ seed * 374761393;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / 16777216f;
    }

    /** Shows the crack picture of a block at the given stage (0..9), or takes it away (anything else). */
    private static void showCrack(Minecraft mc, Carved c, BlockPos pos, int stage) {
        int[] now = c.cracks.get(pos);
        if (stage < 0 || stage > 9) {
            if (now != null) {
                mc.levelRenderer.destroyBlockProgress(now[0], pos, -1);
                c.cracks.remove(pos);
            }
            return;
        }
        if (now == null) c.cracks.put(pos, now = new int[] {CRACK_ID + c.nextId++, -1});
        if (now[1] != stage) {
            now[1] = stage;
            mc.levelRenderer.destroyBlockProgress(now[0], pos, stage);
        }
    }

    private static void clearCracks(Minecraft mc, Carved c) {
        c.cracks.forEach((pos, v) -> mc.levelRenderer.destroyBlockProgress(v[0], pos, -1));
        c.cracks.clear();
    }

    private static void giveBack(Minecraft mc, Carved c) {
        clearCracks(mc, c);
        c.blocks.forEach((p, state) -> put(c.level, p, state));
        c.blocks.clear();
    }

    private static void put(ClientLevel level, BlockPos p, BlockState state) {
        if (level.hasChunkAt(p) && level.getBlockState(p).isAir()) level.setBlock(p, state, FLAGS);
    }
}
