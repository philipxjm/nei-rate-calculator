package com.philipxjm.neiratecalc.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.philipxjm.neiratecalc.NEIRateCalc;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

/**
 * Global index of every GT recipe by what it produces, so the tree view can
 * find producers for any input. Built once, lazily, on a background thread
 * (a full GTNH install holds a few hundred thousand recipes).
 */
public final class RecipeIndex {

    public static final class Producer {

        public final RecipeMap<?> map;
        public final GTRecipe recipe;

        public Producer(RecipeMap<?> map, GTRecipe recipe) {
            this.map = map;
            this.recipe = recipe;
        }
    }

    private static volatile boolean started;
    private static volatile boolean ready;
    private static Map<Long, List<Producer>> itemProducers;
    private static Map<String, List<Producer>> fluidProducers;

    private RecipeIndex() {}

    public static boolean isReady() {
        return ready;
    }

    /** Kicks off the build if it hasn't run yet; returns immediately. */
    public static synchronized void ensureBuilt() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                build();
            }
        }, "neiratecalc-recipe-index");
        thread.setDaemon(true);
        thread.start();
    }

    private static void build() {
        long startMs = System.currentTimeMillis();
        Map<Long, List<Producer>> items = new HashMap<Long, List<Producer>>();
        Map<String, List<Producer>> fluids = new HashMap<String, List<Producer>>();
        int recipes = 0;
        try {
            for (RecipeMap<?> map : new ArrayList<RecipeMap<?>>(RecipeMap.ALL_RECIPE_MAPS.values())) {
                for (GTRecipe recipe : map.getAllRecipes()) {
                    if (recipe == null || recipe.mFakeRecipe) {
                        continue;
                    }
                    recipes++;
                    if (recipe.mOutputs != null) {
                        for (ItemStack out : recipe.mOutputs) {
                            if (out == null || out.getItem() == null) {
                                continue;
                            }
                            addProducer(items, itemKey(out), map, recipe);
                        }
                    }
                    if (recipe.mFluidOutputs != null) {
                        for (FluidStack out : recipe.mFluidOutputs) {
                            if (out == null || out.getFluid() == null) {
                                continue;
                            }
                            addProducer(
                                fluids,
                                out.getFluid()
                                    .getName(),
                                map,
                                recipe);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            NEIRateCalc.LOG.error("Recipe index build failed", t);
        }
        itemProducers = items;
        fluidProducers = fluids;
        ready = true;
        NEIRateCalc.LOG.info(
            "Indexed {} GT recipes ({} items, {} fluids) in {} ms",
            recipes,
            items.size(),
            fluids.size(),
            System.currentTimeMillis() - startMs);
    }

    private static <K> void addProducer(Map<K, List<Producer>> index, K key, RecipeMap<?> map, GTRecipe recipe) {
        List<Producer> list = index.get(key);
        if (list == null) {
            list = new ArrayList<Producer>();
            index.put(key, list);
        }
        list.add(new Producer(map, recipe));
    }

    public static long itemKey(ItemStack stack) {
        return ((long) Item.getIdFromItem(stack.getItem()) << 16) | (stack.getItemDamage() & 0xFFFFL);
    }

    public static List<Producer> forItem(ItemStack stack) {
        if (!ready || stack == null || stack.getItem() == null) {
            return Collections.emptyList();
        }
        List<Producer> list = itemProducers.get(itemKey(stack));
        return list != null ? list : Collections.<Producer>emptyList();
    }

    public static List<Producer> forFluid(FluidStack fluid) {
        if (!ready || fluid == null || fluid.getFluid() == null) {
            return Collections.emptyList();
        }
        List<Producer> list = fluidProducers.get(
            fluid.getFluid()
                .getName());
        return list != null ? list : Collections.<Producer>emptyList();
    }
}
