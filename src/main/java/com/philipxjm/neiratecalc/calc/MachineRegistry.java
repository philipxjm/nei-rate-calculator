package com.philipxjm.neiratecalc.calc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.philipxjm.neiratecalc.Config;

import gregtech.api.util.GTRecipe;

/**
 * Machine choices per recipe map (keyed by the map's unlocalizedName, which is
 * also its NEI handler identity). Multiblock entries live in MultiblockData.
 */
public final class MachineRegistry {

    public static final MachinePreset SINGLEBLOCK = MachinePreset.single()
        .oc(OCKind.NORMAL)
        .build();

    private static final Map<String, List<MachinePreset>> MULTIS = new HashMap<String, List<MachinePreset>>();
    /** Recipe maps with no singleblock machine (EBF, assline, GT++ maps...). */
    private static final Set<String> MULTI_ONLY = new HashSet<String>();
    /** Remembers the last configuration used per recipe map this session. */
    private static final Map<String, MachineConfig> LAST_USED = new HashMap<String, MachineConfig>();

    private static boolean loaded;

    private MachineRegistry() {}

    private static synchronized void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            MultiblockData.register();
        }
    }

    static void addMulti(String mapUnlocalizedName, MachinePreset preset) {
        List<MachinePreset> list = MULTIS.get(mapUnlocalizedName);
        if (list == null) {
            list = new ArrayList<MachinePreset>();
            MULTIS.put(mapUnlocalizedName, list);
        }
        list.add(preset);
    }

    static void multiOnly(String... mapUnlocalizedNames) {
        for (String name : mapUnlocalizedNames) {
            MULTI_ONLY.add(name);
        }
    }

    /** All machine choices for a recipe map; never empty. */
    public static List<MachinePreset> presetsFor(String mapUnlocalizedName) {
        ensureLoaded();
        List<MachinePreset> result = new ArrayList<MachinePreset>();
        List<MachinePreset> multis = MULTIS.get(mapUnlocalizedName);
        boolean hasMultis = multis != null && !multis.isEmpty();
        if (!hasMultis || !MULTI_ONLY.contains(mapUnlocalizedName)) {
            result.add(SINGLEBLOCK);
        }
        if (hasMultis) {
            result.addAll(multis);
        }
        return result;
    }

    /**
     * A fresh config for this map+recipe: last session choice if compatible,
     * otherwise defaults (config tier, recipe minimum tier, fitting coils).
     */
    public static MachineConfig defaultConfig(String mapUnlocalizedName, GTRecipe recipe) {
        ensureLoaded();
        int minTier = MachineConfig.minTierFor(recipe.mEUt);
        MachineConfig last = LAST_USED.get(mapUnlocalizedName);
        MachineConfig cfg;
        if (last != null) {
            cfg = last.copy();
            cfg.machines = 1;
        } else {
            List<MachinePreset> presets = presetsFor(mapUnlocalizedName);
            MachinePreset chosen = presets.get(0);
            if (Config.preferMultiblock) {
                for (MachinePreset p : presets) {
                    if (p.multiblock) {
                        chosen = p;
                        break;
                    }
                }
            }
            cfg = new MachineConfig(chosen);
            cfg.tier = Config.defaultTier >= 0 ? Config.defaultTier : minTier;
            cfg.amps = Config.defaultAmps;
        }
        if (cfg.tier < minTier) {
            cfg.tier = minTier;
        }
        if (cfg.preset.oc == OCKind.HEAT && cfg.machineHeat() < recipe.mSpecialValue) {
            cfg.fitCoilsTo(recipe.mSpecialValue);
        }
        cfg.clampToPreset();
        return cfg;
    }

    public static void rememberChoice(String mapUnlocalizedName, MachineConfig cfg) {
        LAST_USED.put(mapUnlocalizedName, cfg.copy());
    }
}
