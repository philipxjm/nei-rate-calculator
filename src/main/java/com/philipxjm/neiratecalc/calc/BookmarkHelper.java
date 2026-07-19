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

    /** What a K-press over the bookmark panel should calculate. */
    public static class GroupTarget {

        public final ItemStack stack;
        /** Fluid form of the stack, used when nothing crafts the item. */
        public final FluidStack fluidAlt;

        GroupTarget(ItemStack stack, FluidStack fluidAlt) {
            this.stack = stack;
            this.fluidAlt = fluidAlt;
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
            return new GroupTarget(rootItem.itemStack, StackInfo.getFluid(rootItem.itemStack));
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

    private static List<Recipe.RecipeId> bookmarkedRecipeIds() {
        List<Recipe.RecipeId> ids = new ArrayList<Recipe.RecipeId>();
        for (BookmarkGrid grid : allGrids()) {
            for (int i = 0; i < grid.size(); i++) {
                BookmarkItem item = grid.getBookmarkItem(i);
                if (item == null || item.recipeId == null) {
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
        if (producers == null || producers.isEmpty()) {
            return -1;
        }
        try {
            for (Recipe.RecipeId rid : bookmarkedRecipeIds()) {
                if (!resultMatches(rid, stack, fluid)) {
                    continue;
                }
                int best = -1;
                int bestScore = -1;
                for (int i = 0; i < producers.size(); i++) {
                    RecipeIndex.Producer p = producers.get(i);
                    int score = 0;
                    if (p.map.unlocalizedName.equals(rid.getHandleName())) {
                        score += 1000;
                    }
                    score += ingredientOverlap(rid, p.recipe);
                    if (score > bestScore) {
                        bestScore = score;
                        best = i;
                    }
                }
                // Same recipe map, or a strong ingredient match across maps.
                if (bestScore >= 1000 || bestScore >= 3) {
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

    private static boolean resultMatches(Recipe.RecipeId rid, ItemStack stack, FluidStack fluid) {
        ItemStack result = rid.getResult();
        if (result == null || result.getItem() == null) {
            return false;
        }
        if (stack != null) {
            return RecipeIndex.itemKey(result) == RecipeIndex.itemKey(stack);
        }
        if (fluid != null) {
            FluidStack asFluid = StackInfo.getFluid(result);
            return asFluid != null && asFluid.getFluid() == fluid.getFluid();
        }
        return false;
    }

    private static int ingredientOverlap(Recipe.RecipeId rid, gregtech.api.util.GTRecipe recipe) {
        int score = 0;
        for (ItemStack ing : rid.getIngredients()) {
            if (ing == null || ing.getItem() == null) {
                continue;
            }
            FluidStack ingFluid = StackInfo.getFluid(ing);
            if (ingFluid != null && recipe.mFluidInputs != null) {
                boolean hit = false;
                for (FluidStack in : recipe.mFluidInputs) {
                    if (in != null && in.getFluid() == ingFluid.getFluid()) {
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    score += 2;
                    continue;
                }
            }
            if (recipe.mInputs != null) {
                for (ItemStack in : recipe.mInputs) {
                    if (in == null || in.getItem() != ing.getItem()) {
                        continue;
                    }
                    score += 1;
                    if (in.getItemDamage() == ing.getItemDamage()) {
                        score += 1;
                    }
                    break;
                }
            }
        }
        return score;
    }
}
