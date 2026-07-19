package com.philipxjm.neiratecalc.calc;

import gregtech.api.util.GTRecipe;

/**
 * Static description of one machine choice for a recipe map: a singleblock
 * tier ladder or a specific multiblock with its parallel/speed/EU rules.
 */
public class MachinePreset {

    /** Max parallels as a function of the user's structure/tier choices. */
    public interface ParallelRule {

        int maxParallels(MachineConfig cfg, GTRecipe recipe);
    }

    /** Base duration multiplier (lower = faster) for the user's choices. */
    public interface SpeedRule {

        double durationModifier(MachineConfig cfg, GTRecipe recipe);
    }

    /** EU/t multiplier (lower = cheaper) for the user's choices. */
    public interface EuRule {

        double euModifier(MachineConfig cfg, GTRecipe recipe);
    }

    public final String id;
    public final String name;
    public final boolean multiblock;
    public final boolean usesCoils;
    public final boolean usesPipeCasing;
    /** Adds the EBF's +100K heat capacity per energy hatch tier above MV. */
    public final boolean tierHeatBonus;
    /** Multiplier on coil heat when computing machine heat (Zyngen: 2x). */
    public final double coilHeatMultiplier;
    /** Extra tiered structure part (anvil, solenoid, unit casing...). */
    public final String casingLabel;
    public final int casingMin;
    public final int casingMax;
    public final String[] casingNames;
    public final OCKind oc;
    public final ParallelRule parallels;
    public final SpeedRule speed;
    public final EuRule eu;
    public final String note;

    private MachinePreset(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.multiblock = b.multiblock;
        this.usesCoils = b.usesCoils;
        this.usesPipeCasing = b.usesPipeCasing;
        this.tierHeatBonus = b.tierHeatBonus;
        this.coilHeatMultiplier = b.coilHeatMultiplier;
        this.casingLabel = b.casingLabel;
        this.casingMin = b.casingMin;
        this.casingMax = b.casingMax;
        this.casingNames = b.casingNames;
        this.oc = b.oc;
        this.parallels = b.parallels;
        this.speed = b.speed;
        this.eu = b.eu;
        this.note = b.note;
    }

    public String casingName(int tier) {
        int idx = tier - casingMin;
        if (casingNames != null && idx >= 0 && idx < casingNames.length) {
            return casingNames[idx];
        }
        return "T" + tier;
    }

    public static Builder single() {
        return new Builder("single", "Singleblock", false);
    }

    public static Builder multi(String id, String name) {
        return new Builder(id, name, true);
    }

    public static class Builder {

        private final String id;
        private final String name;
        private final boolean multiblock;
        private boolean usesCoils;
        private boolean usesPipeCasing;
        private boolean tierHeatBonus;
        private double coilHeatMultiplier = 1.0;
        private String casingLabel;
        private int casingMin;
        private int casingMax;
        private String[] casingNames;
        private OCKind oc = OCKind.NORMAL;
        private ParallelRule parallels = new ParallelRule() {

            @Override
            public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                return 1;
            }
        };
        private SpeedRule speed = new SpeedRule() {

            @Override
            public double durationModifier(MachineConfig cfg, GTRecipe recipe) {
                return 1.0;
            }
        };
        private EuRule eu = new EuRule() {

            @Override
            public double euModifier(MachineConfig cfg, GTRecipe recipe) {
                return 1.0;
            }
        };
        private String note;

        private Builder(String id, String name, boolean multiblock) {
            this.id = id;
            this.name = name;
            this.multiblock = multiblock;
        }

        public Builder oc(OCKind kind) {
            this.oc = kind;
            if (kind == OCKind.HEAT) {
                this.usesCoils = true;
            }
            return this;
        }

        public Builder coils() {
            this.usesCoils = true;
            return this;
        }

        public Builder pipeCasing() {
            this.usesPipeCasing = true;
            return this;
        }

        public Builder tierHeatBonus() {
            this.tierHeatBonus = true;
            return this;
        }

        public Builder coilHeatMultiplier(double mult) {
            this.coilHeatMultiplier = mult;
            return this;
        }

        public Builder casing(String label, int min, int max, String... names) {
            this.casingLabel = label;
            this.casingMin = min;
            this.casingMax = max;
            this.casingNames = names.length > 0 ? names : null;
            return this;
        }

        public Builder parallels(final int fixed) {
            this.parallels = new ParallelRule() {

                @Override
                public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                    return fixed;
                }
            };
            return this;
        }

        public Builder parallelsPerTier(final int perTier) {
            this.parallels = new ParallelRule() {

                @Override
                public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                    return Math.max(1, perTier * Math.max(1, cfg.tier));
                }
            };
            return this;
        }

        public Builder parallels(ParallelRule rule) {
            this.parallels = rule;
            return this;
        }

        public Builder speed(final double durationModifier) {
            this.speed = new SpeedRule() {

                @Override
                public double durationModifier(MachineConfig cfg, GTRecipe recipe) {
                    return durationModifier;
                }
            };
            return this;
        }

        public Builder speed(SpeedRule rule) {
            this.speed = rule;
            return this;
        }

        public Builder euModifier(final double modifier) {
            this.eu = new EuRule() {

                @Override
                public double euModifier(MachineConfig cfg, GTRecipe recipe) {
                    return modifier;
                }
            };
            return this;
        }

        public Builder eu(EuRule rule) {
            this.eu = rule;
            return this;
        }

        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public MachinePreset build() {
            return new MachinePreset(this);
        }
    }
}
