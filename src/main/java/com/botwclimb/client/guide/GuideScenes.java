package com.botwclimb.client.guide;

import com.botwclimb.BotwClimb;
import com.botwclimb.client.GuiShapes;
import com.botwclimb.client.StaminaHud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.List;

/** The guide's scenes: the stamina altar, and the shrine terminal with the plot it leads to. */
public final class GuideScenes {
    private GuideScenes() {}

    private static final BlockState WHITE = Blocks.WHITE_CONCRETE.defaultBlockState(), SNOW = Blocks.SNOW_BLOCK.defaultBlockState(), GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final int SILVER = 0xFFE8EEF2, INLAY = 0xFF1B5A50, MARK = 0xFF00F25A;

    /**
     * The table of values pops up at TABLE_START; from THROW0 its items are thrown onto the altar one by one, most valuable first
     * (the latest leaves THROW_W ticks later), each flying FLIGHT ticks and filling the Stamina Bar over FILL; it rolls up by TABLE_END.
     */
    /** The Stamina Bar and its note come first (WHEEL_AT), then the right-click (CLICK_AT) that opens the table. */
    private static final int WHEEL_AT = 86, CLICK_AT = 154;
    private static final int TABLE_START = CLICK_AT + 22, THROW0 = TABLE_START + 24, THROW_W = 280, FLIGHT = 12, FILL = 10;
    private static final int TABLE_END = THROW0 + THROW_W + FLIGHT + FILL + 20;
    private static final int TABLE_W = 166;
    /** Points for all 8 upgrades: what the altar can hold. */
    private static final float TOTAL = total();

    public static final GuideScreen.Scene ALTAR = scene("altar", () -> new ItemStack(BotwClimb.ALTAR_ITEM.get()), TABLE_END + 136, new int[] {24, WHEEL_AT, CLICK_AT, THROW0, TABLE_END + 6, TABLE_END + 68}, (s, t) -> {
        // while the table is out the diorama glides right, clear of it (done before the list unrolls, back only once it rolled up)
        float open = Math.max(0f, Math.min(1f, Math.min((t - TABLE_START) / 8f, (TABLE_END - 2 - t) / 8f)));
        if (open > 0) s.slide(open * open * (3 - 2 * open) * s.clearOf(10 + TABLE_W + 18));
        s.plate(WHITE, SNOW, t, 0);
        float pts = pointsAt(t);
        int have = 0; // upgrades bought so far: the altar's rings light per finished quarter, as in the world
        for (float cum = 0; have < 8 && pts >= (cum += com.botwclimb.common.altar.AltarRules.PRICES[have]) - 0.001f; ) have++;
        s.altar(BotwClimb.ALTAR_BLOCK.get().defaultBlockState(), 2, 1, 2, t, 14, have);
        s.note(t, 24, 80, "altar_intro", 2.5f, 1.8f, 2.5f, true);
        s.click(t, CLICK_AT, 2.5f, 2f, 2.5f);
        float[] dst = s.project(2.5f, 1.55f, 2.5f); // where thrown items land: the altar's top, wherever the slide has put it
        if (t >= TABLE_START && t <= TABLE_END - 2) s.screen(g -> values(g, t - TABLE_START, TABLE_END - 2 - t, t, dst));
        s.note(t, WHEEL_AT + 6, CLICK_AT - 4, "altar_wheels", 3.9f, 3.0f, 2.2f, true); // what the bar is, before the table and anything filling it
        // once the table is away, on the left (clear of the bar): what gear is worth, then what changes it
        s.note(t, TABLE_END + 6, TABLE_END + 62, "values_gear", 2.5f, 1.8f, 2.5f, false);
        s.note(t, TABLE_END + 68, TABLE_END + 126, "values_gear_extra", 2.5f, 1.8f, 2.5f, false);
        if (t >= WHEEL_AT) s.flatAt(3.9f, 3.0f, 2.2f, (g, at) -> wheel(g, at[0], at[1], Math.min(1f, (t - WHEEL_AT) / 8f), pts));
    });

    /** The rider in both terminal scenes faces the terminal, which is north of them. */
    private static final float FACE_TERMINAL = 180f;

    public static final GuideScreen.Scene TERMINAL = scene("terminal", () -> new ItemStack(BotwClimb.TERMINAL_ITEM.get()), 250, new int[] {40, 150}, (s, t) -> {
        s.plate(WHITE, SNOW, t, 0);
        s.block(BotwClimb.TERMINAL_BLOCK.get().defaultBlockState(), 2, 1, 1, t, 14);
        float px = 2.5f, pz = 3.3f;
        float rise = t < 175 ? 0f : (float) Math.pow((t - 175) / 24f, 2) * 6f;
        if (t >= 26 && rise < 5.5f) s.player(px, 1f + rise, pz, FACE_TERMINAL);
        s.click(t, 34, 2.5f, 1.6f, 1.5f);
        s.note(t, 40, 110, "terminal_pick", 2.5f, 1.6f, 1.5f, true);
        if (t >= 120 && t < 225) { // the marker on the ground, then the light carrying the rider up
            float grow = Math.min(1f, (t - 120) / 12f), fade = t < 199 ? 1f : 1f - (t - 199) / 26f;
            float pulse = 0.75f + 0.25f * (float) Math.sin(t * 0.5f);
            s.ring(px, 1.01f, pz, 0.15f * grow, 0.75f * grow, argb(0x00F2C8, 0.55f * pulse * fade));
            s.ring(px, 1.012f, pz, 0.75f * grow, 0.82f * grow, argb(0xE8FFF6, 0.9f * fade));
            if (t >= 150) {
                float h = Math.min(1f, (t - 150) / 20f) * 3.5f;
                for (int i = 0; i < 8; i++) {
                    double a0 = i * Math.PI / 4 + t * 0.03, a1 = a0 + Math.PI / 8;
                    s.wall(px + 0.6f * (float) Math.cos(a0), pz + 0.6f * (float) Math.sin(a0), px + 0.6f * (float) Math.cos(a1), pz + 0.6f * (float) Math.sin(a1), 1.01f, 1.01f + h, argb(0x00F2C8, 0.45f * fade));
                }
            }
        }
        s.note(t, 150, 230, "terminal_ride", px, 2.2f, pz, true);
    });

    public static final GuideScreen.Scene PLOT = scene("plot", () -> new ItemStack(Items.GRASS_BLOCK), 210, new int[] {60, 135}, (s, t) -> {
        s.plate(GRASS, GRASS, t, 0);
        s.block(BotwClimb.TERMINAL_BLOCK.get().defaultBlockState(), 2, 1, 1, t, 12);
        if (t >= 20) outline(s, t, Math.min(1f, (t - 20) / 10f));
        float px = 2.5f, pz = 3.3f;
        if (t >= 30) { // the rider comes up out of the ground and lands
            float u = Math.min(1f, (t - 30) / 22f);
            float y = u < 0.55f ? -1.8f + 3.6f * (u / 0.55f) - 0.8f * (float) Math.pow(u / 0.55f, 2) : 1f + 1.0f * (float) Math.sin((u - 0.55f) / 0.45f * Math.PI) * (1f - (u - 0.55f) / 0.45f);
            s.player(px, Math.max(y, -1.8f), pz, FACE_TERMINAL);
            if (t < 80) s.ring(px, 1.01f, pz, 0.1f, 0.8f, argb(0x00F2C8, 0.5f * (1f - (t - 30) / 50f)));
        }
        s.note(t, 60, 128, "plot_island", 0.5f, 1f, 4.5f, false);
        s.click(t, 135, 2.5f, 1.6f, 1.5f);
        s.note(t, 140, 205, "plot_return", 2.5f, 1.6f, 1.5f, true);
    });

    /**
     * Drafts of a new look for the altar, side by side, every upgrade lit: A a low dais, B a stepped pedestal with a gold band,
     * C the low dais in pale stone. Not part of the guide: filmed with -Pguide -Dbotwclimb.guide.concepts to show the choice.
     */
    public static final GuideScreen.Scene CONCEPTS = scene("concepts", () -> new ItemStack(BotwClimb.ALTAR_ITEM.get()), 40, new int[0], (s, t) -> {
        s.plate(WHITE, SNOW, 99, 0);
        String side = "botwclimb:block/stamina_altar_side", top = "botwclimb:block/stamina_altar_top", gold = "botwclimb:block/shrine_terminal_gold";
        String pale = "minecraft:block/calcite", paleTop = "minecraft:block/smooth_stone";
        float p = 1f / 16f;
        // A: a low dais, half as tall as now, with a top plate set in
        s.cuboid(0, 1, 2, 1, 1 + 5 * p, 3, side, side);
        s.cuboid(p, 1 + 5 * p, 2 + p, 1 - p, 1 + 8 * p, 3 - p, top, side);
        rings(s, 0.5f, 1 + 8 * p, 2.5f, 0.9f);
        // B: a stepped pedestal like the terminal's: a wide foot, a narrow column, a gold band and a top plate
        s.cuboid(2, 1, 2, 3, 1 + 3 * p, 3, side, side);
        s.cuboid(2 + 3 * p, 1 + 3 * p, 2 + 3 * p, 3 - 3 * p, 1 + 8 * p, 3 - 3 * p, side, side);
        s.cuboid(2 + 2 * p, 1 + 8 * p, 2 + 2 * p, 3 - 2 * p, 1 + 9 * p, 3 - 2 * p, gold, gold);
        s.cuboid(2 + p, 1 + 9 * p, 2 + p, 3 - p, 1 + 12 * p, 3 - p, top, side);
        rings(s, 2.5f, 1 + 12 * p, 2.5f, 0.9f);
        // C: the low dais in pale stone, so the green glow stands out
        s.cuboid(4, 1, 2, 5, 1 + 5 * p, 3, pale, pale);
        s.cuboid(4 + p, 1 + 5 * p, 2 + p, 5 - p, 1 + 8 * p, 3 - p, paleTop, pale);
        rings(s, 4.5f, 1 + 8 * p, 2.5f, 0.9f);
        String[] names = {"A", "B", "C"};
        for (int i = 0; i < 3; i++) {
            final String n = names[i];
            s.flatAt(0.5f + 2 * i, 1f, 3.6f, (g, at) -> g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, n, (int) at[0], (int) at[1], 0xFFFFFFFF));
        }
    });

    /** The altar's two rings on a top at height y, all lit, sized by {@code k}. */
    private static void rings(GuideScreen s, float cx, float y, float cz, float k) {
        float[][] r = {{0.26f, 0.355f}, {0.125f, 0.195f}};
        for (int ring = 0; ring < 2; ring++)
            for (int q = 0; q < 4; q++) s.arc(cx, y + 0.01f, cz, r[ring][0] * k, r[ring][1] * k, q * 0.25f + 0.01f, (q + 1) * 0.25f - 0.01f, 0xFF00F25A);
    }

    private static final int TABLE_ROWS = 6;
    private static List<ItemStack> table;

    /** Gear the table shows as two sets, priced by the altar's own rule (its diamonds, plus netherite's bonus). */
    private static final List<List<net.minecraft.world.item.Item>> GEAR_SETS = List.of(
            List.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.NETHERITE_PICKAXE, Items.NETHERITE_SWORD),
            List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD));

    private static final java.util.Comparator<ItemStack> BY_VALUE = java.util.Comparator.comparingDouble(st -> -com.botwclimb.common.altar.AltarValues.unitValue(st));

    /**
     * What the table lists, in the order it is thrown: every item most valuable first, except that each gear set (armor and tools)
     * stays together, sorted inside, and sits right below the last item worth at least its best piece. Storage blocks and raw ores
     * are left out: they are worth what they hold, and the ingot is already listed. Every value is the altar's own (unitValue).
     */
    private static List<ItemStack> table() {
        if (table == null || table.isEmpty()) { // empty until a world has sent the values
            var units = new java.util.ArrayList<List<ItemStack>>(); // single items, then the sets: on a tie the single item stays above
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
                if (com.botwclimb.common.altar.AltarValues.looseValue(item) > 0f && !path.endsWith("_block") && !path.startsWith("raw_")
                        && item != Items.ANCIENT_DEBRIS) units.add(List.of(new ItemStack(item)));
            }
            if (units.isEmpty()) return new java.util.ArrayList<>();
            for (var set : GEAR_SETS) {
                var stacks = new java.util.ArrayList<ItemStack>();
                for (var item : set) stacks.add(new ItemStack(item));
                stacks.sort(BY_VALUE);
                units.add(stacks);
            }
            units.sort((u, v) -> BY_VALUE.compare(u.get(0), v.get(0))); // a stable sort, by each unit's best item
            var all = new java.util.ArrayList<ItemStack>();
            for (var u : units) all.addAll(u);
            table = all;
        }
        return table;
    }

    private static float total() {
        float sum = 0;
        for (float p : com.botwclimb.common.altar.AltarRules.PRICES) sum += p;
        return sum;
    }

    private static float value(int i) {
        return com.botwclimb.common.altar.AltarValues.unitValue(table().get(i));
    }

    /**
     * When each thrown item leaves the list, in ticks after THROW0: slowly for the big ones at the top, quicker further down. Only
     * as many are thrown as the Stamina Bar can still take; the rest stay in the list.
     */
    private static float[] throwsAt() {
        int n = 0;
        for (float sum = 0; n < table().size() && sum < TOTAL; n++) sum += value(n);
        float[] at = new float[n];
        float t = 0;
        for (int i = 0; i < n; i++) {
            at[i] = t;
            t += Math.max(5, 16 - i);
        }
        if (t > THROW_W) for (int i = 0; i < n; i++) at[i] *= THROW_W / t; // a longer list (extra values from a datapack) goes quicker
        return at;
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3 - 2 * x);
    }

    /** Points offered by tick {@code t}: each thrown item's value flows in smoothly after it lands. */
    private static float pointsAt(float t) {
        float[] at = throwsAt();
        float p = 0;
        for (int i = 0; i < at.length; i++) p += value(i) * ease((t - THROW0 - at[i] - FLIGHT) / FILL);
        return p;
    }

    /**
     * The table of what offerings are worth, in glass: it pops up as just its title bar and unrolls; its items are then thrown onto
     * the altar one at a time (the list sliding up smoothly behind each), and it rolls back up and pops away. {@code since} and
     * {@code left} are ticks since it came and until it goes, {@code t} the scene's tick, {@code dst} where thrown items land.
     */
    private static void values(net.minecraft.client.gui.GuiGraphics g, float since, float left, float t, float[] dst) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        var items = table();
        int rowH = 16, w = TABLE_W, head = 24, viewH = TABLE_ROWS * rowH, costsH = 43, full = head + viewH + 6 + costsH;
        int x = 10, y = Math.max(14, Math.min((g.guiHeight() - full) / 2 - 10, g.guiHeight() - 50 - full)); // always clear of the buttons and timeline
        float edge = Math.min(since, left); // the opening and closing mirror each other
        float pop = Math.max(0f, Math.min(1f, edge / 6f)), roll = Math.max(0f, Math.min(1f, (edge - 8f) / 10f));
        roll = roll * roll * (3 - 2 * roll);
        int h = head + Math.round((full - head) * roll);
        com.botwclimb.client.ClientCompat.pushPose(g);
        if (pop < 1f) { // a springy pop from the title bar's middle, slightly overshooting
            float q = pop - 1f, k = Math.max(0f, 1f + 2.7f * q * q * q + 1.7f * q * q);
            com.botwclimb.client.ClientCompat.translate(g, x + w / 2f, y + head / 2f, 0);
            com.botwclimb.client.ClientCompat.scale(g, k, k);
            com.botwclimb.client.ClientCompat.translate(g, -(x + w / 2f), -(y + head / 2f), 0);
        }
        com.botwclimb.client.GlassUi.panel(g, x, y, w, h);
        g.drawString(font, Component.translatable("gui.botwclimb.guide.values"), x + 8, y + 8, 0xFFEDF1F6, true);
        com.botwclimb.client.ClientCompat.popPose(g);
        if (roll <= 0f) return;
        g.fill(x + 8, y + 19, x + w - 8, y + 20, 0x44E8EEF2);
        float[] at = throwsAt();
        float shift = 0; // rows gone so far: each thrown row slides out over a few ticks, never a jump
        for (int i = 0; i < at.length; i++) {
            float gap = i + 1 < at.length ? at[i + 1] - at[i] : 8f;
            shift += ease((t - THROW0 - at[i] - 1f) / Math.min(8f, gap));
        }
        int top = y + head, shown = h - head - (roll < 1f ? 0 : 6);
        g.enableScissor(x + 1, top - 1, x + w - 1, top + Math.min(viewH, shown));
        int first = (int) shift;
        // the list moves by a fraction of a pixel, not in whole steps (rounding each row made it stutter)
        com.botwclimb.client.ClientCompat.pushPose(g);
        com.botwclimb.client.ClientCompat.translate(g, 0, -(shift - first) * rowH, 0);
        for (int i = first; i <= first + TABLE_ROWS && i < items.size(); i++) {
            int ry = top + (i - first) * rowH;
            float lift = i < at.length ? t - THROW0 - at[i] : -1f;
            float a = lift < 0 ? 1f : 1f - lift / 4f; // a thrown row's text fades as its item flies off
            var stack = items.get(i);
            if (lift < 0) g.renderItem(stack, x + 8, ry - 1);
            if (a < 0.05f) continue; // the font draws text that faint as opaque
            String v = points(value(i));
            String name = stack.getHoverName().getString(), fit = font.plainSubstrByWidth(name, w - 36 - 10 - font.width(v) - 6);
            if (fit.length() < name.length()) fit = font.plainSubstrByWidth(name, w - 36 - 10 - font.width(v) - 6 - font.width("..")).trim() + ".."; // never runs into its number
            g.drawString(font, fit, x + 28, ry + 4, fade(0xFFD2D8E0, a), true);
            g.drawString(font, v, x + w - 8 - font.width(v), ry + 4, fade(0xFF00F25A, a), true);
        }
        com.botwclimb.client.ClientCompat.popPose(g);
        g.disableScissor();
        // under the list: the points each of the 8 upgrades needs, two rows of four, uncovered as the table unrolls
        int cy = top + viewH + 6;
        if (y + h > cy) {
            g.enableScissor(x + 1, cy, x + w - 1, y + h);
            g.fill(x + 8, cy, x + w - 8, cy + 1, 0x44E8EEF2);
            g.drawString(font, Component.translatable("gui.botwclimb.guide.values_costs"), x + 8, cy + 5, 0xFFEDF1F6, true);
            int cw = (w - 16) / 4;
            for (int i = 0; i < com.botwclimb.common.altar.AltarRules.PRICES.length; i++) {
                int bx = x + 8 + (i % 4) * cw, by = cy + 17 + (i / 4) * 11;
                String n = (i + 1) + ".", p = points(com.botwclimb.common.altar.AltarRules.PRICES[i]);
                g.drawString(font, n, bx, by, 0xFF7D8694, true);
                g.drawString(font, p, bx + font.width(n) + 3, by, 0xFF00F25A, true); // each number right by its upgrade, a gap before the next
            }
            g.disableScissor();
        }
        // the items in the air: from their row's icon onto the altar, growing a little, then sinking in
        for (int i = 0; i < at.length; i++) {
            float p = (t - THROW0 - at[i]) / FLIGHT;
            if (p < 0f || p >= 1f) continue;
            // out of the panel sideways first (fast, then slowing), so it never crosses the title; a low hop on the way
            float ex = 1f - (1f - p) * (1f - p) * (1f - p), sx = x + 16, sy = top + 7;
            float fx = sx + (dst[0] - sx) * ex, fy = sy + (dst[1] - sy) * ease(p) - 14f * (float) Math.sin(Math.PI * p);
            float k = (1f + 0.5f * (float) Math.sin(Math.PI * p)) * (p > 0.75f ? (1f - p) / 0.25f : 1f);
            com.botwclimb.client.ClientCompat.pushPose(g);
            com.botwclimb.client.ClientCompat.translate(g, fx, fy, 0);
            com.botwclimb.client.ClientCompat.scale(g, k, k);
            g.renderItem(items.get(i), -8, -8);
            com.botwclimb.client.ClientCompat.popPose(g);
        }
    }

    private static String points(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.valueOf(Math.round(v * 100) / 100f);
    }

    private static int fade(int argb, float a) {
        return ((int) ((argb >>> 24) * Math.max(0f, Math.min(1f, a))) << 24) | (argb & 0xFFFFFF);
    }

    public static List<GuideScreen.Scene> all() {
        return List.of(ALTAR, TERMINAL, PLOT);
    }

    /** The silver band round the plate's top edge, with green marks drifting round it, as the plot outline is drawn in the world. */
    private static void outline(GuideScreen s, float t, float a) {
        float y = 1.003f, w = 0.14f, in = 0.05f, e = 5f;
        int band = argb(SILVER & 0xFFFFFF, a), inlay = argb(INLAY & 0xFFFFFF, a);
        s.flat(0, 0, e, w, y, band);
        s.flat(0, e - w, e, e, y, band);
        s.flat(0, w, w, e - w, y, band);
        s.flat(e - w, w, e, e - w, y, band);
        float m0 = (w - in) / 2, m1 = m0 + in;
        s.flat(m0, m0, e - m0, m1, y + 0.001f, inlay);
        s.flat(m0, e - m1, e - m0, e - m0, y + 0.001f, inlay);
        s.flat(m0, m1, m1, e - m1, y + 0.001f, inlay);
        s.flat(e - m1, m1, e - m0, e - m1, y + 0.001f, inlay);
        float per = 4 * (e - w), mark = 0.3f;
        for (int i = 0; i < 10; i++) { // marks on one loop round the edge
            float d = ((i * per / 10f) + t * 0.03f) % per;
            float[] p = along(d, e - w, m0, (m0 + m1) / 2);
            float h = mark / 2;
            boolean xSide = p[2] == 0;
            s.flat(p[0] - (xSide ? h : in / 2), p[1] - (xSide ? in / 2 : h), p[0] + (xSide ? h : in / 2), p[1] + (xSide ? in / 2 : h), y + 0.002f, argb(MARK & 0xFFFFFF, a));
        }
    }

    /** A point a distance round the square loop: x, z, and 0 on an x side or 1 on a z side. */
    private static float[] along(float d, float side, float off, float mid) {
        float lo = mid, hi = 5f - mid;
        if (d < side) return new float[] {lo + d / side * (hi - lo), lo, 0};
        d -= side;
        if (d < side) return new float[] {hi, lo + d / side * (hi - lo), 1};
        d -= side;
        if (d < side) return new float[] {hi - d / side * (hi - lo), hi, 0};
        d -= side;
        return new float[] {lo, hi - d / side * (hi - lo), 1};
    }

    /** The Stamina Bar with {@code points} offered: each upgrade quarter fills smoothly towards its price, in order. */
    private static void wheel(net.minecraft.client.gui.GuiGraphics g, float cx, float cy, float pop, float points) {
        float s = 0.85f * pop;
        if (s <= 0.01f) return;
        Matrix4f m = GuiShapes.pose(g);
        StaminaHud.beginDraw(net.minecraft.client.Minecraft.getInstance(), g);
        float[][] r = StaminaHud.RINGS;
        StaminaHud.ring(m, cx, cy, r[0][0] * s, r[0][1] * s, 0f, 1f, 0x00F25A, 1f);
        float cum = 0;
        for (int k = 0; k < 8; k++) {
            float price = com.botwclimb.common.altar.AltarRules.PRICES[k], f = Math.max(0f, Math.min(1f, (points - cum) / price));
            cum += price;
            float r0 = r[k / 4 + 1][0] * s, r1 = r[k / 4 + 1][1] * s, a0 = (k % 4) * 0.25f + 0.006f, a1 = (k % 4 + 1) * 0.25f - 0.006f;
            StaminaHud.ring(m, cx, cy, r0, r1, a0, a1, 0x262B33, 1f);
            if (f > 0.001f) StaminaHud.ring(m, cx, cy, r0, r1, a0, a0 + (a1 - a0) * f, 0x00F25A, 1f);
        }
        StaminaHud.endDraw();
    }

    private static int argb(int rgb, float a) {
        return ((int) (255 * Math.max(0f, Math.min(1f, a))) << 24) | rgb;
    }

    private interface Draw {
        void draw(GuideScreen s, float t);
    }

    private static GuideScreen.Scene scene(String key, java.util.function.Supplier<ItemStack> icon, int length, int[] marks, Draw draw) {
        return new GuideScreen.Scene() {
            public Component title() {
                return Component.translatable("gui.botwclimb.guide." + key + ".title");
            }

            public ItemStack icon() {
                return icon.get();
            }

            public int length() {
                return length;
            }

            public int[] marks() {
                return marks;
            }

            public void draw(GuideScreen s, float t) {
                draw.draw(s, t);
            }
        };
    }
}
