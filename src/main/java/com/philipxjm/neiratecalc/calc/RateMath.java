package com.philipxjm.neiratecalc.calc;

import gregtech.api.enums.GTValues;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/**
 * Mirrors GregTech's ProcessingLogic sequence: parallels are limited by the
 * EU budget first (ParallelHelper), then the whole parallel batch overclocks
 * (OverclockCalculator with amperage OC for multiblocks).
 */
public final class RateMath {

    private RateMath() {}

    public static RateResult compute(GTRecipe recipe, MachineConfig cfg) {
        try {
            return computeInner(recipe, cfg);
        } catch (Throwable t) {
            // Never crash the GUI over math; fall back to unclocked values.
            int duration = Math.max(1, recipe.mDuration);
            return RateResult.of(1, duration, recipe.mEUt);
        }
    }

    private static RateResult computeInner(GTRecipe recipe, MachineConfig cfg) {
        MachinePreset p = cfg.preset;
        long voltage = GTValues.V[Math.max(0, Math.min(GTValues.V.length - 1, cfg.tier))];
        long amps = p.multiblock ? Math.max(1, cfg.amps) : 1;
        long availableEUt = voltage * amps;

        double euModifier = p.eu.euModifier(cfg, recipe);
        OverclockCalculator calc = new OverclockCalculator().setRecipeEUt(recipe.mEUt)
            .setEUt(voltage)
            .setAmperage(amps)
            .setDuration(Math.max(1, recipe.mDuration))
            .setDurationModifier(p.speed.durationModifier(cfg, recipe))
            .setEUtDiscount(euModifier)
            .setAmperageOC(p.multiblock);

        switch (p.oc) {
            case PERFECT:
                calc.enablePerfectOC();
                break;
            case HEAT:
                calc.setHeatOC(true)
                    .setHeatDiscount(true)
                    .setRecipeHeat(Math.max(0, recipe.mSpecialValue))
                    .setMachineHeat(cfg.machineHeat());
                break;
            case NONE:
                calc.setNoOverclock(true);
                break;
            default:
                break;
        }

        if (p.oc == OCKind.HEAT && cfg.machineHeat() < recipe.mSpecialValue) {
            return RateResult
                .fail(String.format("Needs %,dK heat (coils give %,dK)", recipe.mSpecialValue, cfg.machineHeat()));
        }

        double heatMult = calc.calculateHeatDiscountMultiplier();
        long effEUt = (long) Math.ceil(recipe.mEUt * euModifier * heatMult);
        if (effEUt > availableEUt) {
            return RateResult.fail(
                String.format("Needs %,d EU/t, have %,d (%s x%dA)", effEUt, availableEUt, GTValues.VN[cfg.tier], amps));
        }

        int maxParallels = Math.max(1, p.parallels.maxParallels(cfg, recipe));
        int parallels = (int) Math.max(1, Math.min(maxParallels, availableEUt / Math.max(1, effEUt)));

        calc.setParallel(parallels)
            .calculate();

        int duration = Math.max(1, calc.getDuration());
        long eut = calc.getConsumption();
        if (eut < 0) {
            eut = recipe.mEUt;
        }
        return RateResult.of(parallels, duration, eut);
    }
}
