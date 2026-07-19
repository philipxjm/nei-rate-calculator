package com.philipxjm.neiratecalc;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Mod settings from config/neiratecalc.cfg. */
public final class Config {

    /** Default machine tier for new calculations; -1 = recipe minimum. */
    public static int defaultTier = -1;
    /** Default energy hatch amperage assumed for multiblocks. */
    public static int defaultAmps = 2;

    private Config() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();
            defaultTier = cfg.getInt(
                "defaultTier",
                Configuration.CATEGORY_GENERAL,
                -1,
                -1,
                15,
                "Default voltage tier for calculations (0=ULV..15); -1 uses each recipe's minimum tier");
            defaultAmps = cfg.getInt(
                "defaultAmps",
                Configuration.CATEGORY_GENERAL,
                2,
                1,
                1_048_576,
                "Default energy hatch amperage assumed for multiblocks (a standard hatch supplies 2A)");
        } finally {
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
    }
}
