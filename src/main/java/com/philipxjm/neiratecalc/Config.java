package com.philipxjm.neiratecalc;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Mod settings from config/neiratecalc.cfg, editable in-game. */
public final class Config {

    /** Default machine tier for new calculations; -1 = recipe minimum. */
    public static int defaultTier = -1;
    /** Default energy hatch amperage assumed for multiblocks. */
    public static int defaultAmps = 2;
    /** Pick a multiblock over the singleblock when one exists. */
    public static boolean preferMultiblock = true;
    /** Global cap on multiblock parallels (models partial builds). */
    public static int maxParallels = 256;

    private static Configuration cfg;

    private Config() {}

    public static void load(File file) {
        cfg = new Configuration(file);
        cfg.load();
        sync();
    }

    private static void sync() {
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
        preferMultiblock = cfg.getBoolean(
            "preferMultiblock",
            Configuration.CATEGORY_GENERAL,
            true,
            "Default to a multiblock machine when the recipe map has one");
        maxParallels = cfg.getInt(
            "maxParallels",
            Configuration.CATEGORY_GENERAL,
            256,
            1,
            4096,
            "Cap on multiblock parallels used in calculations");
        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    /** Persist the current field values back to disk. */
    public static void save() {
        if (cfg == null) {
            return;
        }
        cfg.get(Configuration.CATEGORY_GENERAL, "defaultTier", -1)
            .set(defaultTier);
        cfg.get(Configuration.CATEGORY_GENERAL, "defaultAmps", 2)
            .set(defaultAmps);
        cfg.get(Configuration.CATEGORY_GENERAL, "preferMultiblock", true)
            .set(preferMultiblock);
        cfg.get(Configuration.CATEGORY_GENERAL, "maxParallels", 256)
            .set(maxParallels);
        cfg.save();
    }
}
