package com.philipxjm.neiratecalc.nei;

import java.awt.Point;
import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;

import com.philipxjm.neiratecalc.NEIRateCalc;
import com.philipxjm.neiratecalc.calc.BookmarkHelper;
import com.philipxjm.neiratecalc.gui.GuiRateCalculator;
import com.philipxjm.neiratecalc.gui.GuiRecipeTree;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.Widget;
import codechicken.nei.WidgetContainer;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.NEIRecipeWidget;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;

/**
 * Opens the rate calculator when the calculator key is pressed while hovering
 * a GregTech recipe inside NEI's recipe viewer.
 */
public class RateCalcInputHandler implements IContainerInputHandler {

    public static final String KEY_ID = "neiratecalc.calculate";

    /** GuiRecipe keeps its widget tree in a private ScrollContainer. */
    private static Field containerField;
    private static boolean containerLookupFailed;

    private static WidgetContainer getWidgetContainer(GuiRecipe<?> gui) {
        if (containerLookupFailed) {
            return null;
        }
        try {
            if (containerField == null) {
                containerField = GuiRecipe.class.getDeclaredField("container");
                containerField.setAccessible(true);
            }
            return (WidgetContainer) containerField.get(gui);
        } catch (Throwable t) {
            containerLookupFailed = true;
            NEIRateCalc.LOG.error("Could not access NEI's recipe widget container; calculator disabled", t);
            return null;
        }
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {
        if (!NEIClientConfig.isKeyHashDown(KEY_ID)) {
            return false;
        }
        Point mouse = GuiDraw.getMousePosition();
        if (gui instanceof GuiRecipe && tryOpenFromRecipeWidget((GuiRecipe<?>) gui, mouse)) {
            return true;
        }
        return tryOpenFromBookmarks(gui, mouse);
    }

    /** K over a bookmark group or item opens the tree for its top product. */
    private boolean tryOpenFromBookmarks(GuiContainer gui, Point mouse) {
        BookmarkHelper.GroupTarget target = BookmarkHelper.targetUnderMouse(mouse.x, mouse.y);
        if (target == null) {
            return false;
        }
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiRecipeTree(gui, target.stack, target.fluidAlt, target.amount));
        return true;
    }

    private boolean tryOpenFromRecipeWidget(GuiRecipe<?> recipeGui, Point mouse) {
        WidgetContainer container = getWidgetContainer(recipeGui);
        if (container == null) {
            return false;
        }

        Widget widget = container.getWidgetUnderMouse(mouse.x, mouse.y);
        if (!(widget instanceof NEIRecipeWidget)) {
            return false;
        }

        RecipeHandlerRef ref = ((NEIRecipeWidget) widget).getRecipeHandlerRef();
        if (ref == null) {
            return false;
        }
        IRecipeHandler handler = ref.handler;
        int recipeIndex = ref.recipeIndex;

        // The viewer may wrap handlers for search filtering
        // (SearchRecipeHandler is package-private, hence reflection);
        // translate the index back into the real handler's recipe space.
        if ("codechicken.nei.recipe.SearchRecipeHandler".equals(
            handler.getClass()
                .getName())) {
            try {
                java.lang.reflect.Method refMethod = handler.getClass()
                    .getMethod("ref", int.class);
                refMethod.setAccessible(true);
                recipeIndex = (Integer) refMethod.invoke(handler, recipeIndex);
                java.lang.reflect.Field originalField = handler.getClass()
                    .getField("original");
                originalField.setAccessible(true);
                handler = (IRecipeHandler) originalField.get(handler);
            } catch (Throwable t) {
                NEIRateCalc.LOG.error("Could not unwrap NEI search handler", t);
                return false;
            }
        }

        if (!(handler instanceof GTNEIDefaultHandler)) {
            return false;
        }
        GTNEIDefaultHandler gtHandler = (GTNEIDefaultHandler) handler;
        if (recipeIndex < 0 || recipeIndex >= gtHandler.arecipes.size()) {
            return false;
        }
        TemplateRecipeHandler.CachedRecipe cached = gtHandler.arecipes.get(recipeIndex);
        if (!(cached instanceof GTNEIDefaultHandler.CachedDefaultRecipe)) {
            return false;
        }

        GTRecipe recipe = ((GTNEIDefaultHandler.CachedDefaultRecipe) cached).mRecipe;
        RecipeMap<?> recipeMap = gtHandler.getRecipeMap();

        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiRateCalculator(recipeGui, recipeMap, recipe));
        return true;
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        return false;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
}
