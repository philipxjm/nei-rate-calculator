package com.philipxjm.neiratecalc.calc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTRecipe;

/**
 * Solves the whole production plan as a linear program instead of top-down
 * tree propagation: one variable per step (crafts/min), a balance constraint
 * per goods that is both produced and consumed (production >= consumption,
 * so surplus is allowed and recycling loops net out), the root's net output
 * fixed to the target. With a pinned step the root output becomes a variable
 * that gets maximized first, then total machine activity is minimized.
 */
public final class PlanSolver {

    public static class Step {

        public final GTRecipe recipe;
        public final double craftsPerMin; // per machine, from RateMath
        public final String signature; // identical columns share rates for display
        final Map<String, Double> produced = new LinkedHashMap<String, Double>();
        final Map<String, Double> consumed = new LinkedHashMap<String, Double>();

        Step(GTRecipe recipe, double craftsPerMin, String signature) {
            this.recipe = recipe;
            this.craftsPerMin = craftsPerMin;
            this.signature = signature;
        }
    }

    public static class PlanResult {

        public final int status;
        public final double[] rates; // crafts/min per step
        public final double rootRate; // net root goods per minute
        public final Map<String, double[]> flows; // goods -> {produced, consumed}

        PlanResult(int status, double[] rates, double rootRate, Map<String, double[]> flows) {
            this.status = status;
            this.rates = rates;
            this.rootRate = rootRate;
            this.flows = flows;
        }
    }

    private PlanSolver() {}

    public static String itemKey(ItemStack stack) {
        return "i" + RecipeIndex.itemKey(stack);
    }

    public static String fluidKey(FluidStack fluid) {
        return "f" + fluid.getFluid()
            .getName();
    }

    /** Extracts a step's per-craft goods flows, registering display names. */
    public static Step makeStep(GTRecipe recipe, double craftsPerMin, String signature, Map<String, String> names,
        Map<String, Boolean> isFluid) {
        Step step = new Step(recipe, craftsPerMin, signature);
        if (recipe.mOutputs != null) {
            for (int i = 0; i < recipe.mOutputs.length; i++) {
                ItemStack out = recipe.mOutputs[i];
                if (out == null || out.getItem() == null) continue;
                int chance = 10000;
                if (recipe.mOutputChances != null && i < recipe.mOutputChances.length) {
                    chance = recipe.mOutputChances[i];
                }
                add(step.produced, itemKey(out), out.stackSize * (chance / 10000.0));
                names.put(itemKey(out), out.getDisplayName());
                isFluid.put(itemKey(out), false);
            }
        }
        if (recipe.mFluidOutputs != null) {
            for (FluidStack out : recipe.mFluidOutputs) {
                if (out == null || out.getFluid() == null) continue;
                add(step.produced, fluidKey(out), out.amount);
                names.put(fluidKey(out), out.getLocalizedName());
                isFluid.put(fluidKey(out), true);
            }
        }
        if (recipe.mInputs != null) {
            for (ItemStack in : recipe.mInputs) {
                if (in == null || in.stackSize <= 0 || in.getItem() == null) continue;
                add(step.consumed, itemKey(in), in.stackSize);
                names.put(itemKey(in), in.getDisplayName());
                isFluid.put(itemKey(in), false);
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack in : recipe.mFluidInputs) {
                if (in == null || in.amount <= 0 || in.getFluid() == null) continue;
                add(step.consumed, fluidKey(in), (double) in.amount);
                names.put(fluidKey(in), in.getLocalizedName());
                isFluid.put(fluidKey(in), true);
            }
        }
        return step;
    }

    private static void add(Map<String, Double> map, String key, double amount) {
        Double old = map.get(key);
        map.put(key, (old == null ? 0 : old) + amount);
    }

    /**
     * @param target      net root goods per minute (rate mode)
     * @param pinnedStep  index of the pinned step, or -1
     * @param pinnedCount fixed machine count for the pinned step
     */
    public static PlanResult solve(List<Step> steps, String rootGoods, double target, int pinnedStep,
        double pinnedCount) {
        int k = steps.size();
        boolean pinMode = pinnedStep >= 0;
        int vars = pinMode ? k + 1 : k; // extra var: root output rate

        List<String> goods = new ArrayList<String>();
        for (Step step : steps) {
            for (String key : step.produced.keySet()) {
                if (!goods.contains(key)) goods.add(key);
            }
            for (String key : step.consumed.keySet()) {
                if (!goods.contains(key)) goods.add(key);
            }
        }

        List<double[]> rows = new ArrayList<double[]>();
        List<Integer> rels = new ArrayList<Integer>();
        List<Double> rhs = new ArrayList<Double>();

        for (String key : goods) {
            boolean isRoot = key.equals(rootGoods);
            boolean produced = false;
            boolean consumed = false;
            double[] row = new double[vars];
            for (int i = 0; i < k; i++) {
                Step step = steps.get(i);
                Double p = step.produced.get(key);
                Double c = step.consumed.get(key);
                double net = (p == null ? 0 : p) - (c == null ? 0 : c);
                row[i] = net;
                produced |= p != null;
                consumed |= c != null;
            }
            if (isRoot) {
                if (pinMode) {
                    row[k] = -1;
                    rows.add(row);
                    rels.add(Simplex.EQ);
                    rhs.add(0.0);
                } else {
                    rows.add(row);
                    rels.add(Simplex.EQ);
                    rhs.add(target);
                }
            } else if (produced && consumed) {
                rows.add(row);
                rels.add(Simplex.GE);
                rhs.add(0.0);
            }
        }

        if (pinMode) {
            double[] row = new double[vars];
            row[pinnedStep] = 1;
            rows.add(row);
            rels.add(Simplex.EQ);
            rhs.add(pinnedCount * steps.get(pinnedStep).craftsPerMin);
        }

        double[][] a = rows.toArray(new double[0][]);
        int[] rel = new int[rels.size()];
        double[] b = new double[rhs.size()];
        for (int i = 0; i < rel.length; i++) {
            rel[i] = rels.get(i);
            b[i] = rhs.get(i);
        }

        double[] rates;
        double rootRate;
        if (pinMode) {
            // Phase A: maximize the root output the pin supports.
            double[] cMax = new double[vars];
            cMax[k] = 1;
            Simplex.Result phaseA = Simplex.maximize(cMax, a, rel, b);
            if (phaseA.status != Simplex.OPTIMAL) {
                return new PlanResult(phaseA.status, null, 0, null);
            }
            rootRate = phaseA.x[k];
            // Phase B: minimize activity at that output level.
            double[][] a2 = new double[a.length + 1][];
            int[] rel2 = new int[rel.length + 1];
            double[] b2 = new double[b.length + 1];
            System.arraycopy(a, 0, a2, 0, a.length);
            System.arraycopy(rel, 0, rel2, 0, rel.length);
            System.arraycopy(b, 0, b2, 0, b.length);
            double[] fix = new double[vars];
            fix[k] = 1;
            a2[a.length] = fix;
            rel2[rel.length] = Simplex.EQ;
            b2[b.length] = rootRate;
            Simplex.Result phaseB = Simplex.maximize(negOnes(vars, k), a2, rel2, b2);
            if (phaseB.status != Simplex.OPTIMAL) {
                return new PlanResult(phaseB.status, null, 0, null);
            }
            rates = phaseB.x;
        } else {
            Simplex.Result result = Simplex.maximize(negOnes(vars, vars), a, rel, b);
            if (result.status != Simplex.OPTIMAL) {
                return new PlanResult(result.status, null, 0, null);
            }
            rates = result.x;
            rootRate = target;
        }

        // Identical steps (same recipe+config in different branches) have
        // interchangeable columns; split their total evenly for display.
        // The pinned step keeps its exact solved rate.
        double[] finalRates = new double[k];
        System.arraycopy(rates, 0, finalRates, 0, k);
        for (int i = 0; i < k; i++) {
            if (i == pinnedStep) {
                continue;
            }
            String sig = steps.get(i).signature;
            double total = 0;
            int count = 0;
            for (int j = 0; j < k; j++) {
                if (j != pinnedStep && steps.get(j).signature.equals(sig)) {
                    total += rates[j];
                    count++;
                }
            }
            if (count > 1) {
                finalRates[i] = total / count;
            }
        }

        Map<String, double[]> flows = new LinkedHashMap<String, double[]>();
        for (String key : goods) {
            double produced = 0;
            double consumed = 0;
            for (int i = 0; i < k; i++) {
                Step step = steps.get(i);
                Double p = step.produced.get(key);
                Double c = step.consumed.get(key);
                produced += (p == null ? 0 : p) * finalRates[i];
                consumed += (c == null ? 0 : c) * finalRates[i];
            }
            flows.put(key, new double[] { produced, consumed });
        }
        return new PlanResult(Simplex.OPTIMAL, finalRates, rootRate, flows);
    }

    private static double[] negOnes(int vars, int upTo) {
        double[] c = new double[vars];
        for (int i = 0; i < upTo; i++) {
            c[i] = -1;
        }
        return c;
    }
}
