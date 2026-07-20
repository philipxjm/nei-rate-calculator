package com.philipxjm.neiratecalc;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class Config {

    public static int defaultTier = -1;

    public static int defaultAmps = 2;

    public static boolean preferMultiblock = true;

    public static int maxParallels = 256;
    // 0 = per minute, 1 = per second, 2 = per tick
    public static int rateUnit = 0;

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
        rateUnit = cfg.getInt(
            "rateUnit",
            Configuration.CATEGORY_GENERAL,
            0,
            0,
            2,
            "Rate display unit: 0 = per minute, 1 = per second, 2 = per tick");
        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

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
        cfg.get(Configuration.CATEGORY_GENERAL, "rateUnit", 0)
            .set(rateUnit);
        cfg.save();
    }
}
