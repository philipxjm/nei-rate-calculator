package com.philipxjm.neiratecalc.calc;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.HeatingCoilLevel;

/** The user's current machine selection: preset plus structure/tier choices. */
public class MachineConfig {

    public static final String[] PIPE_CASING_NAMES = { "-", "Bronze", "Steel", "Titanium", "Tungstensteel" };

    public MachinePreset preset;
    /** Voltage tier index into GTValues.V (energy hatch tier for multis). */
    public int tier = 1;
    /** Amps of that tier available to the machine (energy hatch amperage). */
    public long amps = 2;
    /** HeatingCoilLevel ordinal; first real coil (Cupronickel) is ordinal 2. */
    public int coilOrdinal = 2;
    /** Pipe casing tier 1..4 (Bronze/Steel/Titanium/Tungstensteel). */
    public int pipeTier = 1;
    /** Preset-specific tiered part (anvil, solenoid, unit casing...). */
    public int casingTier;
    /** How many copies of the machine the user is planning. */
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

    /** Machine heat capacity: coil heat plus the EBF's +100K/tier above MV. */
    public int machineHeat() {
        long heat = (long) (coilHeat() * preset.coilHeatMultiplier);
        if (preset.tierHeatBonus) {
            heat += 100L * Math.max(0, tier - 2);
        }
        return (int) Math.min(Integer.MAX_VALUE, heat);
    }

    /** Clamp structure choices into the preset's valid ranges. */
    public void clampToPreset() {
        if (preset.casingLabel != null) {
            casingTier = Math.max(preset.casingMin, Math.min(preset.casingMax, casingTier));
        }
    }

    /** Picks the lowest coil that satisfies the recipe's heat requirement. */
    public void fitCoilsTo(int recipeHeat) {
        HeatingCoilLevel[] levels = HeatingCoilLevel.values();
        for (int i = 2; i < levels.length; i++) {
            coilOrdinal = i;
            if (machineHeat() >= recipeHeat) {
                return;
            }
        }
    }

    /** Lowest tier whose voltage covers the recipe's base EU/t. */
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
