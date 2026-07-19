package com.philipxjm.neiratecalc.calc;

import com.philipxjm.neiratecalc.calc.MachinePreset.EuRule;
import com.philipxjm.neiratecalc.calc.MachinePreset.ParallelRule;
import com.philipxjm.neiratecalc.calc.MachinePreset.SpeedRule;

import gregtech.api.util.GTRecipe;

/**
 * The multiblock machine table: parallels, speed, EU and overclock rules per
 * recipe map, extracted from GT5-Unofficial 5.09.52.594 sources (the exact
 * version in GTNH 2.9.0-beta-1). File:line references are to that tree.
 */
final class MultiblockData {

    private MultiblockData() {}

    /** HeatingCoilLevel.getTier(): 0 = Cupronickel, 1 = Kanthal, ... */
    private static int coilTier(MachineConfig cfg) {
        return Math.max(0, cfg.coilOrdinal - 2);
    }

    static void register() {
        MachineRegistry.multiOnly(
            "gt.recipe.blastfurnace",
            "gt.recipe.largechemicalreactor",
            "gt.recipe.distillationtower",
            "gt.recipe.vacuumfreezer",
            "gt.recipe.pyro",
            "gt.recipe.craker",
            "gt.recipe.implosioncompressor",
            "gtpp.recipe.multicentrifuge",
            "gtpp.recipe.multielectro",
            "gtpp.recipe.multimixer",
            "gtpp.recipe.simplewasher",
            "gtpp.recipe.cokeoven",
            "gtpp.recipe.multidehydrator",
            "gtpp.recipe.chemicalplant",
            "gtpp.recipe.multiblockrockbreaker",
            "gtpp.recipe.alloyblastsmelter",
            "gg.recipe.precise_assembler",
            "gg.recipe.componentassemblyline",
            "gg.recipe.neutron_activator",
            "kubatech.defusioncrafter",
            "gtnhlanth.recipe.digester",
            "gtnhlanth.recipe.disstank");

        // ---------------- Blast furnace ----------------
        MachineRegistry.addMulti(
            "gt.recipe.blastfurnace",
            MachinePreset.multi("ebf", "Electric Blast Furnace")
                .oc(OCKind.HEAT)
                .tierHeatBonus()
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.blastfurnace",
            // MTEAdvEBF: 8 parallels, 120% faster, 90% EU, coil heat only
            MachinePreset.multi("volcanus", "Volcanus")
                .oc(OCKind.HEAT)
                .parallels(8)
                .speed(1.0 / 2.2)
                .euModifier(0.9)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.blastfurnace",
            MachinePreset.multi("mebf", "Mega Blast Furnace")
                .oc(OCKind.HEAT)
                .tierHeatBonus()
                .parallels(256)
                .note("Feed with multi-amp/laser hatches; set Amps accordingly")
                .build());

        // ---------------- Large chemical reactor ----------------
        MachineRegistry.addMulti(
            "gt.recipe.largechemicalreactor",
            MachinePreset.multi("lcr", "Large Chemical Reactor")
                .oc(OCKind.PERFECT)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.largechemicalreactor",
            MachinePreset.multi("mcr", "Mega Chemical Reactor")
                .oc(OCKind.PERFECT)
                .parallels(256)
                .build());

        // ---------------- Distillation ----------------
        MachineRegistry.addMulti(
            "gt.recipe.distillationtower",
            MachinePreset.multi("dt", "Distillation Tower")
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.distillationtower",
            // MTEAdvDistillationTower tower mode: 12 parallels, 250% speed
            MachinePreset.multi("dangote_t", "Dangote Distillus (tower)")
                .parallels(12)
                .speed(1.0 / 3.5)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.distillationtower",
            // MTEMegaDistillationTower tower mode; setSpeedBonus(1.2) is a
            // duration x1.2 as coded (tooltip claims faster - upstream bug)
            MachinePreset.multi("mdt_t", "Mega Distillation Tower")
                .parallels(256)
                .speed(1.2)
                .euModifier(0.9)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.distillery",
            MachinePreset.multi("dangote_d", "Dangote Distillus (distillery)")
                .casing("Tower height", 2, 12)
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return Math.max(1, (int) (2 * Math.floor((cfg.casingTier + 1) / 3f)) * Math.max(1, cfg.tier));
                    }
                })
                .speed(0.5)
                .euModifier(0.15)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.distillery",
            MachinePreset.multi("mdt_d", "Mega DT (distillery mode)")
                .casing("Middle slices", 1, 5)
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return 256 * (1 + (cfg.casingTier + 1) / 2);
                    }
                })
                .speed(1.5)
                .euModifier(0.5)
                .build());

        // ---------------- Vacuum freezer ----------------
        MachineRegistry.addMulti(
            "gt.recipe.vacuumfreezer",
            MachinePreset.multi("vf", "Vacuum Freezer")
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.vacuumfreezer",
            MachinePreset.multi("cryo", "Cryogenic Freezer")
                .parallels(8)
                .speed(1.0 / 2.2)
                .euModifier(0.9)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.vacuumfreezer",
            MachinePreset.multi("mvf", "Mega Vacuum Freezer")
                .parallels(256)
                .note("T2 subspace-coolant perfect OCs not modeled")
                .build());

        // ---------------- Multi smelter ----------------
        MachineRegistry.addMulti(
            "gt.recipe.furnace",
            // MTEMultiFurnace: fixed 4 EU/t x 128 ticks per item, parallels
            // 4 << (coil ordinal - 1); modifiers below rebase whatever the
            // NEI furnace recipe carries onto those constants.
            MachinePreset.multi("multismelter", "Multi Smelter")
                .coils()
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return 4 << Math.max(1, cfg.coilOrdinal - 1);
                    }
                })
                .speed(new SpeedRule() {

                    @Override
                    public double durationModifier(MachineConfig cfg, GTRecipe recipe) {
                        return 128.0 / Math.max(1, recipe.mDuration);
                    }
                })
                .eu(new EuRule() {

                    @Override
                    public double euModifier(MachineConfig cfg, GTRecipe recipe) {
                        return 4.0 / Math.max(1, recipe.mEUt);
                    }
                })
                .build());

        // ---------------- Pyrolyse oven ----------------
        MachineRegistry.addMulti(
            "gt.recipe.pyro",
            MachinePreset.multi("pyrolyse", "Pyrolyse Oven")
                .coils()
                .speed(new SpeedRule() {

                    @Override
                    public double durationModifier(MachineConfig cfg, GTRecipe recipe) {
                        return 2.0 / (1 + coilTier(cfg));
                    }
                })
                .note("Kanthal coils = 100% speed; each tier above is faster")
                .build());

        // ---------------- Oil cracking ----------------
        EuRule crackerEu = new EuRule() {

            @Override
            public double euModifier(MachineConfig cfg, GTRecipe recipe) {
                return 1.0 - Math.min(0.1 * (coilTier(cfg) + 1), 0.5);
            }
        };
        MachineRegistry.addMulti(
            "gt.recipe.craker",
            MachinePreset.multi("cracker", "Oil Cracking Unit")
                .coils()
                .eu(crackerEu)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.craker",
            MachinePreset.multi("moc", "Mega Oil Cracker")
                .coils()
                .parallels(256)
                .eu(crackerEu)
                .build());

        // ---------------- Implosion ----------------
        MachineRegistry.addMulti(
            "gt.recipe.implosioncompressor",
            MachinePreset.multi("implosion", "Implosion Compressor")
                .build());

        // ---------------- Large Fluid Extractor ----------------
        MachineRegistry.addMulti(
            "gt.recipe.fluidextractor",
            MachinePreset.multi("lfe", "Large Fluid Extractor")
                .coils()
                .casing("Solenoid tier", 2, 9)
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return Math.max(1, 8 * cfg.casingTier);
                    }
                })
                .speed(new SpeedRule() {

                    @Override
                    public double durationModifier(MachineConfig cfg, GTRecipe recipe) {
                        return 1.0 / (1.5 + 0.1 * coilTier(cfg));
                    }
                })
                .eu(new EuRule() {

                    @Override
                    public double euModifier(MachineConfig cfg, GTRecipe recipe) {
                        return 0.8 * Math.pow(0.9, coilTier(cfg));
                    }
                })
                .build());

        // ---------------- GT++ industrial machines ----------------
        MachineRegistry.addMulti(
            "gt.recipe.macerator",
            MachinePreset.multi("macstack", "Industrial Maceration Stack")
                .casing("Controller tier", 1, 2)
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return Math.max(1, (cfg.casingTier == 1 ? 2 : 8) * Math.max(1, cfg.tier));
                    }
                })
                .speed(1.0 / 1.6)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.wiremill",
            MachinePreset.multi("wirefactory", "Industrial Wire Factory")
                .parallelsPerTier(4)
                .speed(1.0 / 3.0)
                .euModifier(0.75)
                .build());
        MachineRegistry.addMulti(
            "gtpp.recipe.multicentrifuge",
            MachinePreset.multi("indcentrifuge", "Industrial Centrifuge")
                .parallelsPerTier(6)
                .speed(1.0 / 2.25)
                .euModifier(0.9)
                .build());
        MachineRegistry.addMulti(
            "gtpp.recipe.multielectro",
            MachinePreset.multi("indelectro", "Industrial Electrolyzer")
                .parallelsPerTier(2)
                .speed(1.0 / 2.8)
                .euModifier(0.9)
                .build());
        MachineRegistry.addMulti(
            "gtpp.recipe.multimixer",
            MachinePreset.multi("indmixer", "Industrial Mixing Machine")
                .parallelsPerTier(8)
                .speed(1.0 / 3.5)
                .build());

        MachinePreset washPlant = MachinePreset.multi("washplant", "Industrial Wash Plant")
            .parallelsPerTier(4)
            .speed(1.0 / 5.0)
            .build();
        MachineRegistry.addMulti("gt.recipe.orewasher", washPlant);
        MachineRegistry.addMulti("gtpp.recipe.simplewasher", washPlant);
        MachineRegistry.addMulti("gt.recipe.chemicalbath", washPlant);

        MachineRegistry.addMulti(
            "gt.recipe.thermalcentrifuge",
            MachinePreset.multi("indthermal", "Industrial Thermal Centrifuge")
                .parallelsPerTier(8)
                .speed(1.0 / 2.5)
                .euModifier(0.8)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.sifter",
            MachinePreset.multi("largesifter", "Large Sifter")
                .parallelsPerTier(4)
                .speed(1.0 / 5.0)
                .euModifier(0.75)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.cuttingsaw",
            MachinePreset.multi("cutfactory", "Industrial Cutting Factory")
                .parallelsPerTier(4)
                .speed(1.0 / 3.0)
                .euModifier(0.75)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.extruder",
            MachinePreset.multi("indextruder", "Industrial Extrusion Machine")
                .parallelsPerTier(4)
                .speed(1.0 / 3.5)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.hammer",
            MachinePreset.multi("sledgehammer", "Industrial Sledgehammer")
                .casing("Anvil tier", 1, 4)
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return Math.max(1, 8 * cfg.casingTier * Math.max(1, cfg.tier));
                    }
                })
                .speed(0.5)
                .build());

        MachinePreset platePress = MachinePreset.multi("platepress", "Industrial Material Press")
            .parallelsPerTier(4)
            .speed(1.0 / 6.0)
            .build();
        MachineRegistry.addMulti("gt.recipe.metalbender", platePress);
        MachineRegistry.addMulti("gt.recipe.press", platePress);

        MachineRegistry.addMulti(
            "gt.recipe.arcfurnace",
            MachinePreset.multi("indarc", "Industrial Arc Furnace")
                .casing("Width tier", 1, 7)
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return Math.max(1, (2 * cfg.casingTier + 1) * Math.max(1, cfg.tier));
                    }
                })
                .speed(1.0 / 3.5)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.plasmaarcfurnace",
            MachinePreset.multi("indarcplasma", "Industrial Arc Furnace (plasma)")
                .casing("Width tier", 1, 7)
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return Math.max(1, 8 * (2 * cfg.casingTier + 1) * Math.max(1, cfg.tier));
                    }
                })
                .speed(1.0 / 3.5)
                .build());

        MachineRegistry.addMulti(
            "gtpp.recipe.cokeoven",
            MachinePreset.multi("indcokeoven", "Industrial Coke Oven")
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return 6 + 12 * Math.max(1, cfg.tier);
                    }
                })
                .eu(new EuRule() {

                    @Override
                    public double euModifier(MachineConfig cfg, GTRecipe recipe) {
                        return (100.0 - Math.max(1, cfg.tier) * 4) / 100.0;
                    }
                })
                .build());
        MachineRegistry.addMulti(
            "gtpp.recipe.multidehydrator",
            MachinePreset.multi("utupu", "Utupu-Tanuri (Dehydrator)")
                .oc(OCKind.HEAT)
                .parallels(4)
                .speed(1.0 / 2.2)
                .euModifier(0.5)
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.alloysmelter",
            // Zyngen: heat OC with 2x coil heat, parallels coil level x tier
            MachinePreset.multi("zyngen", "Zyngen")
                .oc(OCKind.HEAT)
                .coilHeatMultiplier(2.0)
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return Math.max(1, (coilTier(cfg) + 1) * Math.max(1, cfg.tier));
                    }
                })
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.fluidheater",
            MachinePreset.multi("indfluidheater", "Industrial Fluid Heater")
                .parallelsPerTier(8)
                .speed(1.0 / 2.2)
                .euModifier(0.9)
                .build());
        MachineRegistry.addMulti(
            "gtpp.recipe.chemicalplant",
            MachinePreset.multi("chemplant", "ExxonMobil Chemical Plant")
                .coils()
                .pipeCasing()
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return Math.max(1, 2 * cfg.pipeTier);
                    }
                })
                .speed(new SpeedRule() {

                    @Override
                    public double durationModifier(MachineConfig cfg, GTRecipe recipe) {
                        return 2.0 / (1.0 + coilTier(cfg));
                    }
                })
                .note("Solid casing tier must cover the recipe tier (not checked)")
                .build());
        MachineRegistry.addMulti(
            "gtpp.recipe.multiblockrockbreaker",
            MachinePreset.multi("rockbreaker", "Industrial Rock Breaker")
                .parallelsPerTier(8)
                .speed(1.0 / 3.0)
                .euModifier(0.75)
                .build());
        MachineRegistry.addMulti(
            "gtpp.recipe.alloyblastsmelter",
            MachinePreset.multi("abs", "Alloy Blast Smelter")
                .build());

        // ---------------- goodgenerator ----------------
        MachineRegistry.addMulti(
            "gg.recipe.precise_assembler",
            MachinePreset.multi("prass", "Precise Auto-Assembler")
                .casing("Unit casing", 0, 4, "Imprecise", "Mk-I", "Mk-II", "Mk-III", "Mk-IV")
                .note("Recipes need unit casing at or above their mark")
                .build());
        MachineRegistry.addMulti(
            "gt.recipe.assembler",
            MachinePreset.multi("prass_asm", "Precise Auto-Assembler (assembler mode)")
                .casing("Unit casing", 0, 4, "Imprecise", "Mk-I", "Mk-II", "Mk-III", "Mk-IV")
                .parallels(new ParallelRule() {

                    @Override
                    public int maxParallels(MachineConfig cfg, GTRecipe recipe) {
                        return 16 << cfg.casingTier;
                    }
                })
                .speed(0.5)
                .note("No voltage-tier skipping on overclocks (not modeled)")
                .build());
        MachineRegistry.addMulti(
            "gg.recipe.componentassemblyline",
            MachinePreset.multi("coal", "Component Assembly Line")
                .casing(
                    "CoAL casing",
                    0,
                    13,
                    "LV",
                    "MV",
                    "HV",
                    "EV",
                    "IV",
                    "LuV",
                    "ZPM",
                    "UV",
                    "UHV",
                    "UEV",
                    "UIV",
                    "UMV",
                    "UXV",
                    "MAX")
                .speed(new SpeedRule() {

                    @Override
                    public double durationModifier(MachineConfig cfg, GTRecipe recipe) {
                        // Recipe time halves per casing tier above the
                        // component's tier (mSpecialValue).
                        int diff = cfg.casingTier + 1 - Math.max(0, recipe.mSpecialValue);
                        return Math.pow(2, -diff);
                    }
                })
                .build());
        MachineRegistry.addMulti(
            "gg.recipe.neutron_activator",
            MachinePreset.multi("neutron", "Neutron Activator")
                .oc(OCKind.NONE)
                .casing("Pipe layers", 4, 14)
                .speed(new SpeedRule() {

                    @Override
                    public double durationModifier(MachineConfig cfg, GTRecipe recipe) {
                        return Math.pow(0.9, cfg.casingTier - 4);
                    }
                })
                .note("Draws no EU at the controller; accelerators power it")
                .build());

        // ---------------- kubatech / lanthanides ----------------
        MachineRegistry.addMulti(
            "kubatech.defusioncrafter",
            MachinePreset.multi("defc", "DE Fusion Crafter")
                .casing("Core tier", 1, 5, "Bloody Ichorium", "Draconium", "Wyvern", "Awakened", "Chaotic")
                .note("Perfect OCs from core tier above recipe not modeled")
                .build());
        MachineRegistry.addMulti(
            "gtnhlanth.recipe.digester",
            MachinePreset.multi("digester", "Digester")
                .oc(OCKind.PERFECT)
                .coils()
                .note("Coils gate recipe heat; no discount")
                .build());
        MachineRegistry.addMulti(
            "gtnhlanth.recipe.disstank",
            MachinePreset.multi("disstank", "Dissolution Tank")
                .build());
    }
}
