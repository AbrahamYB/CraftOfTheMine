package com.botwclimb.common.altar;

import com.botwclimb.common.climb.ClimbConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * The stamina altar's arithmetic, with no game objects in it: what each upgrade costs, which items to take from an offer
 * (the fewest that cover the price, keeping the overshoot small), and what is left over as credit. The server runs it for
 * real, the screen runs the same code to preview.
 */
public final class AltarRules {
    /** Points needed for upgrade 1 to 8. */
    public static final float[] PRICES = {10f, 14f, 20f, 28f, 38f, 50f, 64f, 80f};
    private static final double EPS = 1.0e-4;

    private AltarRules() {}

    /** One offered stack: the inventory slot it sits in, how many to give, what one is worth. */
    public record Offered(int slot, int count, float unit) {}

    /** What to take from a slot. */
    public record Take(int slot, int count) {}

    /** {@code ok} false = nothing happens and {@code reason} says why. */
    public record Result(boolean ok, String reason, int upgradesAfter, float creditAfter, List<Take> take, float taken, float needed) {
        static Result fail(String reason, int upgrades, float credit) {
            return new Result(false, reason, upgrades, credit, List.of(), 0f, 0f);
        }
    }

    public static float price(int upgrades) {
        return upgrades >= 0 && upgrades < PRICES.length ? PRICES[upgrades] : Float.MAX_VALUE;
    }

    /** What the offer is worth in total. */
    public static double total(List<Offered> offered) {
        double sum = 0;
        for (Offered o : offered) sum += (double) o.count() * o.unit();
        return sum;
    }

    /**
     * Buys the next upgrade with the credit first and then the offer. Items are taken so the overshoot stays small: the
     * smallest item that finishes the job if there is one, otherwise the biggest items that still fit under what is left.
     */
    public static Result evaluate(int upgrades, float credit, List<Offered> offered) {
        if (upgrades >= ClimbConstants.STAMINA_MAX_UPGRADES) return Result.fail("maxed", upgrades, credit);
        double price = price(upgrades), need = price - credit;
        if (need <= EPS) return new Result(true, "", upgrades + 1, (float) Math.max(0, credit - price), List.of(), 0f, 0f);
        if (total(offered) + EPS < need) return Result.fail("not_enough", upgrades, credit);

        // the cheapest way to cover the price: the combination of offered items whose total is the smallest one that still
        // reaches it (a small subset-sum over whole cents, so the overshoot, which becomes credit, is as small as it can be)
        int n = offered.size();
        long[] cents = new long[n];
        long maxUnit = 0, totalCents = 0;
        for (int i = 0; i < n; i++) {
            cents[i] = Math.max(1, Math.round(offered.get(i).unit() * 100.0));
            maxUnit = Math.max(maxUnit, cents[i]);
            totalCents += cents[i] * offered.get(i).count();
        }
        long target = (long) Math.ceil(need * 100.0 - 1.0e-6);
        int cap = (int) Math.min(totalCents, target + maxUnit);
        byte[][] how = new byte[n][cap + 1];   // how many of entry i were added to reach each total in layer i (0 = reached earlier)
        boolean[] reach = new boolean[cap + 1];
        reach[0] = true;
        for (int i = 0; i < n; i++) {
            boolean[] next = reach.clone();
            int c = offered.get(i).count();
            for (int s0 = 0; s0 <= cap; s0++) {
                if (!reach[s0]) continue;
                for (int k = 1; k <= c; k++) {
                    long t = s0 + k * cents[i];
                    if (t > cap) break;
                    if (!next[(int) t]) {
                        next[(int) t] = true;
                        how[i][(int) t] = (byte) Math.min(k, 127);
                    }
                }
            }
            reach = next;
        }
        int best = -1;
        for (int s0 = (int) target; s0 <= cap; s0++) if (reach[s0]) { best = s0; break; }
        if (best < 0) return Result.fail("not_enough", upgrades, credit);
        // walk back through the layers to see how many of each entry make up that total
        int[] used = new int[n];
        int cur = best;
        for (int i = n - 1; i >= 0; i--) {
            int k = how[i][cur];
            if (k > 0 && cur - k * cents[i] >= 0) {
                used[i] = k;
                cur -= (int) (k * cents[i]);
            }
        }
        double taken = best / 100.0;
        List<Take> take = new ArrayList<>();
        for (int i = 0; i < used.length; i++) if (used[i] > 0) take.add(new Take(offered.get(i).slot(), used[i]));
        return new Result(true, "", upgrades + 1, (float) Math.max(0, taken - need), take, (float) taken, (float) need);
    }

    /** How many of an item the leftover credit is paid back as: rounded to the nearest, so a credit that just reaches counts. */
    public static int refundCount(float credit, float unit) {
        if (!(credit > 0f) || !(unit > 0f) || !Float.isFinite(credit) || !Float.isFinite(unit)) return 0;
        return (int) Math.min(2304, Math.floor(credit / unit + 0.5)); // capped: a tiny datapack value must not spawn endless stacks
    }
}
