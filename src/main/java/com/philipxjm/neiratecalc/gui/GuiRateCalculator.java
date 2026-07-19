package com.philipxjm.neiratecalc.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.input.Keyboard;

import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.OverclockCalculator;

/**
 * The in-game rates calculator: pick a machine tier and a machine count for a
 * GregTech recipe and see per-minute input/output rates, overclocked speed and
 * power, and how many machines a target rate needs.
 */
public class GuiRateCalculator extends GuiScreen {

    private static final int PANEL_WIDTH = 300;

    private final GuiScreen parent;
    private final RecipeMap<?> recipeMap;
    private final GTRecipe recipe;

    /** Combined item+fluid outputs for rate display and target selection. */
    private static class OutputEntry {

        final String name;
        final double amountPerCraft; // chance-weighted
        final int chance; // 1..10000
        final boolean fluid;

        OutputEntry(String name, double amountPerCraft, int chance, boolean fluid) {
            this.name = name;
            this.amountPerCraft = amountPerCraft;
            this.chance = chance;
            this.fluid = fluid;
        }
    }

    private static class InputEntry {

        final String name;
        final int amountPerCraft;
        final boolean fluid;

        InputEntry(String name, int amountPerCraft, boolean fluid) {
            this.name = name;
            this.amountPerCraft = amountPerCraft;
            this.fluid = fluid;
        }
    }

    private final List<OutputEntry> outputs = new ArrayList<OutputEntry>();
    private final List<InputEntry> inputs = new ArrayList<InputEntry>();

    private int tier;
    private final int minTier;
    private int machines = 1;
    private int targetOutput = 0;
    private GuiTextField targetField;

    // Computed by recompute()
    private int ocDuration;
    private long ocConsumption;

    private int panelLeft;
    private int panelTop;

    public GuiRateCalculator(GuiScreen parent, RecipeMap<?> recipeMap, GTRecipe recipe) {
        this.parent = parent;
        this.recipeMap = recipeMap;
        this.recipe = recipe;

        int t = 0;
        while (t < GTValues.V.length - 1 && GTValues.V[t] < recipe.mEUt) {
            t++;
        }
        this.minTier = t;
        this.tier = t;

        collectStacks();
        recompute();
    }

    private void collectStacks() {
        if (recipe.mOutputs != null) {
            for (int i = 0; i < recipe.mOutputs.length; i++) {
                ItemStack stack = recipe.mOutputs[i];
                if (stack == null) continue;
                int chance = 10000;
                if (recipe.mOutputChances != null && i < recipe.mOutputChances.length) {
                    chance = recipe.mOutputChances[i];
                }
                outputs.add(
                    new OutputEntry(
                        stack.getDisplayName(),
                        stack.stackSize * (chance / 10000.0),
                        chance,
                        false));
            }
        }
        if (recipe.mFluidOutputs != null) {
            for (FluidStack fluid : recipe.mFluidOutputs) {
                if (fluid == null) continue;
                outputs.add(new OutputEntry(fluid.getLocalizedName(), fluid.amount, 10000, true));
            }
        }
        if (recipe.mInputs != null) {
            for (ItemStack stack : recipe.mInputs) {
                // GregTech marks non-consumed inputs (programmed circuits, molds)
                // with stack size 0; they don't participate in rates.
                if (stack == null || stack.stackSize <= 0) continue;
                inputs.add(new InputEntry(stack.getDisplayName(), stack.stackSize, false));
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack fluid : recipe.mFluidInputs) {
                if (fluid == null || fluid.amount <= 0) continue;
                inputs.add(new InputEntry(fluid.getLocalizedName(), fluid.amount, true));
            }
        }
    }

    private void recompute() {
        try {
            OverclockCalculator calc = new OverclockCalculator().setRecipeEUt(recipe.mEUt)
                .setDuration(recipe.mDuration)
                .setEUt(GTValues.V[tier])
                .calculate();
            ocDuration = Math.max(1, calc.getDuration());
            ocConsumption = calc.getConsumption();
        } catch (Throwable t) {
            // Fall back to unclocked values rather than crash the GUI.
            ocDuration = Math.max(1, recipe.mDuration);
            ocConsumption = recipe.mEUt;
        }
    }

    private double craftsPerMinutePerMachine() {
        return 1200.0 / ocDuration;
    }

    @Override
    public void initGui() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = 24;

        buttonList.clear();
        int rowY = panelTop + 24;
        buttonList.add(new GuiButton(0, panelLeft + 60, rowY, 20, 20, "<"));
        buttonList.add(new GuiButton(1, panelLeft + 160, rowY, 20, 20, ">"));
        buttonList.add(new GuiButton(2, panelLeft + 60, rowY + 24, 20, 20, "-"));
        buttonList.add(new GuiButton(3, panelLeft + 160, rowY + 24, 20, 20, "+"));
        buttonList.add(new GuiButton(4, panelLeft + 60, rowY + 48, 20, 20, "<"));
        buttonList.add(new GuiButton(5, panelLeft + 160, rowY + 48, 20, 20, ">"));

        targetField = new GuiTextField(fontRendererObj, panelLeft + 60, rowY + 74, 96, 16);
        targetField.setText("64");
        targetField.setMaxStringLength(10);

        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int step = isShiftKeyDown() ? 10 : 1;
        switch (button.id) {
            case 0:
                tier = Math.max(minTier, tier - 1);
                break;
            case 1:
                tier = Math.min(GTValues.V.length - 1, tier + 1);
                break;
            case 2:
                machines = Math.max(1, machines - step);
                break;
            case 3:
                machines = Math.min(9999, machines + step);
                break;
            case 4:
                if (!outputs.isEmpty()) targetOutput = (targetOutput + outputs.size() - 1) % outputs.size();
                break;
            case 5:
                if (!outputs.isEmpty()) targetOutput = (targetOutput + 1) % outputs.size();
                break;
        }
        recompute();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        targetField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        targetField.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void updateScreen() {
        targetField.updateCursorCounter();
    }

    private double parseTarget() {
        try {
            return Double.parseDouble(targetField.getText().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String fmt(double v) {
        if (v >= 100) return String.format("%,.0f", v);
        if (v >= 1) return String.format("%.2f", v);
        return String.format("%.3f", v);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        String machineName = StatCollector.translateToLocal(recipeMap.unlocalizedName);
        String title = EnumChatFormatting.GOLD + machineName + EnumChatFormatting.RESET + " - Rate Calculator";
        drawCenteredString(fontRendererObj, title, width / 2, panelTop, 0xFFFFFF);

        int rowY = panelTop + 24;
        int labelX = panelLeft;
        int valueX = panelLeft + 84;

        // Tier row
        fontRendererObj.drawString("Tier", labelX, rowY + 6, 0xAAAAAA);
        String tierText = GTValues.VN[tier] + " (" + String.format("%,d", GTValues.V[tier]) + " EU/t)";
        drawCenteredString(fontRendererObj, tierText, panelLeft + 120, rowY + 6, 0xFFFFFF);

        // Machines row
        fontRendererObj.drawString("Machines", labelX, rowY + 30, 0xAAAAAA);
        drawCenteredString(fontRendererObj, String.valueOf(machines), panelLeft + 120, rowY + 30, 0xFFFFFF);

        // Target output selector row
        fontRendererObj.drawString("Output", labelX, rowY + 54, 0xAAAAAA);
        String outName = outputs.isEmpty() ? "-" : trim(outputs.get(targetOutput).name, 12);
        drawCenteredString(fontRendererObj, outName, panelLeft + 120, rowY + 54, 0xFFFFFF);

        // Target rate field
        fontRendererObj.drawString("Target/min", labelX, rowY + 78, 0xAAAAAA);
        targetField.drawTextBox();

        double craftsPerMin = craftsPerMinutePerMachine();
        int y = rowY + 100;

        fontRendererObj.drawString(
            String.format(
                "Time/craft: %s%.2fs%s   EU/t: %s%,d%s   EU/craft: %,d",
                EnumChatFormatting.AQUA,
                ocDuration / 20.0,
                EnumChatFormatting.RESET,
                EnumChatFormatting.AQUA,
                ocConsumption,
                EnumChatFormatting.RESET,
                ocConsumption * ocDuration),
            labelX,
            y,
            0xFFFFFF);
        y += 11;
        fontRendererObj.drawString(
            String.format(
                "Crafts/min: %s per machine, %s total",
                fmt(craftsPerMin),
                fmt(craftsPerMin * machines)),
            labelX,
            y,
            0xFFFFFF);
        y += 14;

        fontRendererObj.drawString(EnumChatFormatting.GREEN + "Outputs (/min, all machines):", labelX, y, 0xFFFFFF);
        y += 11;
        for (OutputEntry out : outputs) {
            String chanceNote = out.chance < 10000 ? String.format(" (%.1f%%)", out.chance / 100.0) : "";
            String unit = out.fluid ? " L" : "";
            fontRendererObj.drawString(
                "  " + trim(out.name, 26)
                    + chanceNote
                    + ": "
                    + EnumChatFormatting.GREEN
                    + fmt(out.amountPerCraft * craftsPerMin * machines)
                    + unit,
                labelX,
                y,
                0xFFFFFF);
            y += 10;
        }
        y += 4;

        fontRendererObj.drawString(EnumChatFormatting.RED + "Inputs (/min, all machines):", labelX, y, 0xFFFFFF);
        y += 11;
        for (InputEntry in : inputs) {
            String unit = in.fluid ? " L" : "";
            fontRendererObj.drawString(
                "  " + trim(in.name, 26)
                    + ": "
                    + EnumChatFormatting.RED
                    + fmt(in.amountPerCraft * craftsPerMin * machines)
                    + unit,
                labelX,
                y,
                0xFFFFFF);
            y += 10;
        }
        y += 6;

        // Machines needed for target
        double target = parseTarget();
        if (target > 0 && !outputs.isEmpty()) {
            OutputEntry out = outputs.get(targetOutput);
            double perMachine = out.amountPerCraft * craftsPerMin;
            if (perMachine > 0) {
                int needed = (int) Math.ceil(target / perMachine);
                fontRendererObj.drawString(
                    String.format(
                        "%s%d machines%s at %s for %s %s/min",
                        EnumChatFormatting.GOLD,
                        needed,
                        EnumChatFormatting.RESET,
                        GTValues.VN[tier],
                        fmt(target),
                        trim(out.name, 20)),
                    labelX,
                    y,
                    0xFFFFFF);
                y += 12;
            }
        }

        if (recipe.mSpecialValue > 0 && "gt.recipe.blastfurnace".equals(recipeMap.unlocalizedName)) {
            fontRendererObj.drawString(
                EnumChatFormatting.YELLOW + String.format("Requires %,dK coil heat", recipe.mSpecialValue),
                labelX,
                y,
                0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
