package com.philipxjm.neiratecalc.calc;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.HeatingCoilLevel;

public class MachineConfig {

    public static final String[] PIPE_CASING_NAMES = { "-", "Bronze", "Steel", "Titanium", "Tungstensteel" };

    public MachinePreset preset;

    public int tier = 1;

    public long amps = 2;

    public int coilOrdinal = 2;

    public int pipeTier = 1;

    public int casingTier;

    public int machines = 1;

    public MachineConfig(MachinePreset preset) {
        this.preset = preset;
    }

    public long coilHeat() {
        HeatingCoilLevel[] levels = HeatingCoilLevel.values();
        int idx = Math.max(0, Math.min(levels.length - 1, coilOrdinal));
        return levels[idx].getHeat();
    }

    public String coilName() {
        HeatingCoilLevel[] levels = HeatingCoilLevel.values();
        int idx = Math.max(0, Math.min(levels.length - 1, coilOrdinal));
        return levels[idx].getName();
    }

    public String pipeCasingName() {
        return PIPE_CASING_NAMES[Math.max(1, Math.min(4, pipeTier))];
    }

    public int machineHeat() {
        long heat = (long) (coilHeat() * preset.coilHeatMultiplier);
        if (preset.tierHeatBonus) {
            heat += 100L * Math.max(0, tier - 2);
        }
        return (int) Math.min(Integer.MAX_VALUE, heat);
    }

    public void clampToPreset() {
        if (preset.casingLabel != null) {
            casingTier = Math.max(preset.casingMin, Math.min(preset.casingMax, casingTier));
        }
    }

    public void fitCoilsTo(int recipeHeat) {
        HeatingCoilLevel[] levels = HeatingCoilLevel.values();
        for (int i = 2; i < levels.length; i++) {
            coilOrdinal = i;
            if (machineHeat() >= recipeHeat) {
                return;
            }
        }
    }

    public static int minTierFor(long recipeEUt) {
        int t = 0;
        while (t < GTValues.V.length - 1 && GTValues.V[t] < recipeEUt) {
            t++;
        }
        return t;
    }

    public MachineConfig copy() {
        MachineConfig c = new MachineConfig(preset);
        c.tier = tier;
        c.amps = amps;
        c.coilOrdinal = coilOrdinal;
        c.pipeTier = pipeTier;
        c.casingTier = casingTier;
        c.machines = machines;
        return c;
    }
}
