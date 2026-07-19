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

public final class BookmarkHelper {

    private static Field storageField;
    private static Field namespacesField;
    private static java.lang.reflect.Method activeGridMethod;
    private static java.lang.reflect.Method mouseContextMethod;
    private static boolean broken;

    private BookmarkHelper() {}

    public static class Scope {

        final BookmarkGrid grid;
        final int groupId;

        public Scope(BookmarkGrid grid, int groupId) {
            this.grid = grid;
            this.groupId = groupId;
        }
    }

    public static class GroupTarget {

        public final ItemStack stack;

        public final FluidStack fluidAlt;

        public final double amount;

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

    // Expansion gate: a non-ingredient entry whose stack IS the target, i.e.
    // the bookmark tree contains something that produces it.
    private static BookmarkItem findResultEntry(ItemStack stack, FluidStack fluid, Scope scope) {
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
                if (item == null || item.recipeId == null
                    || item.itemStack == null
                    || item.itemStack.getItem() == null) {
                    continue;
                }
                if (scope != null && item.groupId != scope.groupId) {
                    continue;
                }
                if (item.type == BookmarkItem.BookmarkItemType.INGREDIENT) {
                    continue;
                }
                if (stack != null && RecipeIndex.itemKey(item.itemStack) == RecipeIndex.itemKey(stack)) {
                    return item;
                }
                if (fluid != null) {
                    FluidStack asFluid = StackInfo.getFluid(item.itemStack);
                    if (asFluid != null && asFluid.getFluid() == fluid.getFluid()) {
                        return item;
                    }
                }
            }
        }
        return null;
    }

    public static int findBookmarked(ItemStack stack, FluidStack fluid, List<RecipeIndex.Producer> producers) {
        return findBookmarked(stack, fluid, producers, null);
    }

    public static int findBookmarked(ItemStack stack, FluidStack fluid, List<RecipeIndex.Producer> producers,
        Scope scope) {
        if (producers == null || producers.isEmpty()) {
            return -1;
        }
        try {
            BookmarkItem entry = findResultEntry(stack, fluid, scope);
            if (entry == null) {
                return -1;
            }
            Recipe.RecipeId rid = entry.recipeId;
            int best = 0;
            int bestScore = -1;
            for (int i = 0; i < producers.size(); i++) {
                RecipeIndex.Producer p = producers.get(i);
                boolean mapMatch = p.map.unlocalizedName.equals(rid.getHandleName());
                int[] match = ingredientMatch(rid, p.recipe);
                int score = (mapMatch ? 1000 : 0) + match[0] * 10;
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            return best;
        } catch (Throwable t) {
            if (!broken) {
                broken = true;
                NEIRateCalc.LOG.error("Bookmark matching failed; bookmark preference disabled", t);
            }
        }
        return -1;
    }

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
