package com.philipxjm.neiratecalc.calc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.philipxjm.neiratecalc.NEIRateCalc;

import codechicken.nei.ItemPanels;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.StackInfo;

/**
 * Reads NEI's bookmark panel so a bookmarked recipe becomes the preferred
 * producer in the tree. Bookmarks store a RecipeId (handler name = the recipe
 * map/category unlocalizedName, result, ingredients); we match that back onto
 * the indexed GT recipes.
 */
public final class BookmarkHelper {

    /** BookmarkPanel.storage and BookmarkStorage.namespaces are protected. */
    private static Field storageField;
    private static Field namespacesField;
    private static java.lang.reflect.Method activeGridMethod;
    private static java.lang.reflect.Method mouseContextMethod;
    private static boolean broken;

    private BookmarkHelper() {}

    /**
     * Restricts recipe lookups to one bookmark group (the tree K was pressed
     * on); null scope means all bookmarks everywhere.
     */
    public static class Scope {

        final BookmarkGrid grid;
        final int groupId;

        public Scope(BookmarkGrid grid, int groupId) {
            this.grid = grid;
            this.groupId = groupId;
        }
    }

    /** What a K-press over the bookmark panel should calculate. */
    public static class GroupTarget {

        public final ItemStack stack;
        /** Fluid form of the stack, used when nothing crafts the item. */
        public final FluidStack fluidAlt;
        /** The amount configured on the bookmark, as target per minute. */
        public final double amount;
        /** Recipe lookups stay inside the group that was clicked. */
        public final Scope scope;

        GroupTarget(ItemStack stack, FluidStack fluidAlt, double amount, Scope scope) {
            this.stack = stack;
            this.fluidAlt = fluidAlt;
            this.amount = amount;
            this.scope = scope;
        }
    }

    private static Object storage() throws Exception {
        if (storageField == null) {
            storageField = findField(ItemPanels.bookmarkPanel.getClass(), "storage");
            storageField.setAccessible(true);
        }
        return storageField.get(ItemPanels.bookmarkPanel);
    }

    private static BookmarkGrid activeGrid() {
        if (broken) {
            return null;
        }
        try {
            Object storage = storage();
            if (activeGridMethod == null) {
                activeGridMethod = storage.getClass()
                    .getMethod("getActiveGrid");
            }
            return (BookmarkGrid) activeGridMethod.invoke(storage);
        } catch (Throwable t) {
            broken = true;
            NEIRateCalc.LOG.error("Cannot read NEI bookmark grid", t);
            return null;
        }
    }

    /**
     * Resolves a K-press over the bookmark panel: hovering a bookmark group
     * (its bracket or any of its items) targets the group's top product;
     * hovering an ungrouped item targets that item. Null when the mouse is
     * not over anything usable.
     */
    public static GroupTarget targetUnderMouse(int mouseX, int mouseY) {
        try {
            if (!ItemPanels.bookmarkPanel.contains(mouseX, mouseY)) {
                return null;
            }
            BookmarkGrid grid = activeGrid();
            if (grid == null) {
                return null;
            }
            int groupId = -1;
            BookmarkItem hovered = null;
            codechicken.nei.bookmark.BookmarksGridSlot slot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseX, mouseY);
            if (slot != null) {
                groupId = slot.getGroupId();
                hovered = slot.getBookmarkItem();
            } else {
                if (mouseContextMethod == null) {
                    mouseContextMethod = BookmarkGrid.class.getDeclaredMethod("getMouseContext", int.class, int.class);
                    mouseContextMethod.setAccessible(true);
                }
                Object ctx = mouseContextMethod.invoke(grid, mouseX, mouseY);
                if (ctx instanceof BookmarkGrid.BookmarkMouseContext) {
                    groupId = ((BookmarkGrid.BookmarkMouseContext) ctx).groupId;
                }
            }

            BookmarkItem rootItem = null;
            if (groupId > BookmarkGrid.DEFAULT_GROUP_ID) {
                // Top non-ingredient entry of the group = the final product.
                for (int i = 0; i < grid.size(); i++) {
                    BookmarkItem item = grid.getBookmarkItem(i);
                    if (item == null || item.groupId != groupId || item.itemStack == null) {
                        continue;
                    }
                    if (item.type != BookmarkItem.BookmarkItemType.INGREDIENT) {
                        rootItem = item;
                        break;
                    }
                    if (rootItem == null) {
                        rootItem = item;
                    }
                }
            } else if (hovered != null) {
                rootItem = hovered;
            }
            if (rootItem == null || rootItem.itemStack == null) {
                return null;
            }
            double amount = Math.max(1, rootItem.getAmount());
            Scope scope = new Scope(grid, Math.max(groupId, BookmarkGrid.DEFAULT_GROUP_ID));
            return new GroupTarget(rootItem.itemStack, StackInfo.getFluid(rootItem.itemStack), amount, scope);
        } catch (Throwable t) {
            if (!broken) {
                broken = true;
                NEIRateCalc.LOG.error("Bookmark panel lookup failed", t);
            }
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<BookmarkGrid> allGrids() {
        List<BookmarkGrid> grids = new ArrayList<BookmarkGrid>();
        if (broken) {
            return grids;
        }
        try {
            Object storage = storage();
            if (namespacesField == null) {
                namespacesField = findField(storage.getClass(), "namespaces");
                namespacesField.setAccessible(true);
            }
            grids.addAll((List<BookmarkGrid>) namespacesField.get(storage));
        } catch (Throwable t) {
            broken = true;
            NEIRateCalc.LOG.error("Cannot read NEI bookmarks; bookmark preference disabled", t);
        }
        return grids;
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static List<Recipe.RecipeId> bookmarkedRecipeIds(Scope scope) {
        List<Recipe.RecipeId> ids = new ArrayList<Recipe.RecipeId>();
        List<BookmarkGrid> grids;
        if (scope != null) {
            grids = new ArrayList<BookmarkGrid>();
            grids.add(scope.grid);
        } else {
            grids = allGrids();
        }
        for (BookmarkGrid grid : grids) {
            for (int i = 0; i < grid.size(); i++) {
                BookmarkItem item = grid.getBookmarkItem(i);
                if (item == null || item.recipeId == null) {
                    continue;
                }
                if (scope != null && item.groupId != scope.groupId) {
                    continue;
                }
                if (!ids.contains(item.recipeId)) {
                    ids.add(item.recipeId);
                }
            }
        }
        return ids;
    }

    /**
     * Index into producers of the recipe the user bookmarked for this target,
     * or -1 when no bookmark matches.
     */
    public static int findBookmarked(ItemStack stack, FluidStack fluid, List<RecipeIndex.Producer> producers) {
        return findBookmarked(stack, fluid, producers, null);
    }

    /**
     * Scoped variant: only recipes bookmarked inside the given group count.
     * <p>
     * Every candidate producer already outputs the target, so the bookmark
     * only has to be identified as "this recipe": same recipe map (NEI's
     * stored handler name) or its stored result appearing among the
     * candidate's outputs, plus at least half its ingredients matching. The
     * stored result alone is NOT required to equal the target — NEI records
     * only a recipe's first output, which misses multi-output recipes.
     */
    public static int findBookmarked(ItemStack stack, FluidStack fluid, List<RecipeIndex.Producer> producers,
        Scope scope) {
        if (producers == null || producers.isEmpty()) {
            return -1;
        }
        try {
            for (Recipe.RecipeId rid : bookmarkedRecipeIds(scope)) {
                int best = -1;
                int bestScore = -1;
                for (int i = 0; i < producers.size(); i++) {
                    RecipeIndex.Producer p = producers.get(i);
                    boolean mapMatch = p.map.unlocalizedName.equals(rid.getHandleName());
                    boolean resultHit = resultInOutputs(rid, p.recipe);
                    if (!mapMatch && !resultHit) {
                        continue;
                    }
                    int[] match = ingredientMatch(rid, p.recipe);
                    if (match[1] > 0 && match[0] * 2 < match[1]) {
                        continue;
                    }
                    int score = (mapMatch ? 1000 : 0) + (resultHit ? 500 : 0) + match[0] * 10;
                    if (score > bestScore) {
                        bestScore = score;
                        best = i;
                    }
                }
                if (best >= 0) {
                    return best;
                }
            }
        } catch (Throwable t) {
            if (!broken) {
                broken = true;
                NEIRateCalc.LOG.error("Bookmark matching failed; bookmark preference disabled", t);
            }
        }
        return -1;
    }

    /** Does the bookmark's stored result appear among this recipe's outputs? */
    private static boolean resultInOutputs(Recipe.RecipeId rid, gregtech.api.util.GTRecipe recipe) {
        ItemStack result = rid.getResult();
        if (result == null || result.getItem() == null) {
            return false;
        }
        if (recipe.mOutputs != null) {
            long key = RecipeIndex.itemKey(result);
            for (ItemStack out : recipe.mOutputs) {
                if (out != null && out.getItem() != null && RecipeIndex.itemKey(out) == key) {
                    return true;
                }
            }
        }
        FluidStack asFluid = StackInfo.getFluid(result);
        if (asFluid != null && recipe.mFluidOutputs != null) {
            for (FluidStack out : recipe.mFluidOutputs) {
                if (out != null && out.getFluid() == asFluid.getFluid()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Returns {matched, total} over the bookmark's usable ingredients. */
    private static int[] ingredientMatch(Recipe.RecipeId rid, gregtech.api.util.GTRecipe recipe) {
        int matched = 0;
        int total = 0;
        for (ItemStack ing : rid.getIngredients()) {
            if (ing == null || ing.getItem() == null) {
                continue;
            }
            total++;
            FluidStack ingFluid = StackInfo.getFluid(ing);
            boolean hit = false;
            if (ingFluid != null && recipe.mFluidInputs != null) {
                for (FluidStack in : recipe.mFluidInputs) {
                    if (in != null && in.getFluid() == ingFluid.getFluid()) {
                        hit = true;
                        break;
                    }
                }
            }
            if (!hit && recipe.mInputs != null) {
                for (ItemStack in : recipe.mInputs) {
                    if (in != null && in.getItem() == ing.getItem()) {
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) {
                matched++;
            }
        }
        return new int[] { matched, total };
    }
}
