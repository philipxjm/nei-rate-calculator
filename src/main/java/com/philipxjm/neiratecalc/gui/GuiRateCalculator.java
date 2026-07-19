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

import com.philipxjm.neiratecalc.calc.MachineConfig;
import com.philipxjm.neiratecalc.calc.MachinePreset;
import com.philipxjm.neiratecalc.calc.MachineRegistry;
import com.philipxjm.neiratecalc.calc.OCKind;
import com.philipxjm.neiratecalc.calc.RateMath;
import com.philipxjm.neiratecalc.calc.RateResult;
import com.philipxjm.neiratecalc.calc.RecipeIndex;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

/**
 * The in-game rates calculator: pick a machine (singleblock tier or a real
 * multiblock with its parallel/speed bonuses), see per-minute rates, and size
 * a bank of machines for a target rate. From here the recipe tree opens.
 */
public class GuiRateCalculator extends GuiScreen {

    private static final int PANEL_WIDTH = 320;
    private static final int ROW_HEIGHT = 22;
    private static final int TREE_BUTTON_ID = 900;

    private final GuiScreen parent;
    private final RecipeMap<?> recipeMap;
    private final GTRecipe recipe;

    /** Combined item+fluid outputs for rate display and target selection. */
    static class OutputEntry {

        final String name;
        final double amountPerCraft; // chance-weighted
        final int chance; // 1..10000
        final ItemStack stack; // null for fluids
        final FluidStack fluid; // null for items

        OutputEntry(String name, double amountPerCraft, int chance, ItemStack stack, FluidStack fluid) {
            this.name = name;
            this.amountPerCraft = amountPerCraft;
            this.chance = chance;
            this.stack = stack;
            this.fluid = fluid;
        }

        boolean isFluid() {
            return fluid != null;
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

    private enum RowKind {
        MACHINE,
        TIER,
        AMPS,
        COILS,
        PIPE,
        CASING,
        MACHINES,
        OUTPUT
    }

    private final List<OutputEntry> outputs = new ArrayList<OutputEntry>();
    private final List<InputEntry> inputs = new ArrayList<InputEntry>();
    private final List<MachinePreset> presets;
    private final List<RowKind> rows = new ArrayList<RowKind>();

    private final MachineConfig cfg;
    private final int minTier;
    private int targetOutput = 0;
    private GuiTextField targetField;
    private RateResult rr;

    private int panelLeft;
    private int panelTop;

    public GuiRateCalculator(GuiScreen parent, RecipeMap<?> recipeMap, GTRecipe recipe) {
        this.parent = parent;
        this.recipeMap = recipeMap;
        this.recipe = recipe;
        this.minTier = MachineConfig.minTierFor(recipe.mEUt);
        this.presets = MachineRegistry.presetsFor(recipeMap.unlocalizedName);
        this.cfg = MachineRegistry.defaultConfig(recipeMap.unlocalizedName, recipe);

        // Warm the tree index in the background while the user reads rates.
        RecipeIndex.ensureBuilt();

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
                    new OutputEntry(stack.getDisplayName(), stack.stackSize * (chance / 10000.0), chance, stack, null));
            }
        }
        if (recipe.mFluidOutputs != null) {
            for (FluidStack fluid : recipe.mFluidOutputs) {
                if (fluid == null) continue;
                outputs.add(new OutputEntry(fluid.getLocalizedName(), fluid.amount, 10000, null, fluid));
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
        rr = RateMath.compute(recipe, cfg);
        MachineRegistry.rememberChoice(recipeMap.unlocalizedName, cfg);
    }

    private void rebuildRows() {
        rows.clear();
        rows.add(RowKind.MACHINE);
        rows.add(RowKind.TIER);
        if (cfg.preset.multiblock) {
            rows.add(RowKind.AMPS);
        }
        if (cfg.preset.usesCoils) {
            rows.add(RowKind.COILS);
        }
        if (cfg.preset.usesPipeCasing) {
            rows.add(RowKind.PIPE);
        }
        if (cfg.preset.casingLabel != null) {
            rows.add(RowKind.CASING);
        }
        rows.add(RowKind.MACHINES);
        rows.add(RowKind.OUTPUT);
    }

    private int rowY(int rowIndex) {
        return panelTop + 16 + rowIndex * ROW_HEIGHT;
    }

    private void layoutControls() {
        rebuildRows();
        buttonList.clear();
        for (int i = 0; i < rows.size(); i++) {
            String left = rows.get(i) == RowKind.MACHINES ? "-" : "<";
            String right = rows.get(i) == RowKind.MACHINES ? "+" : ">";
            buttonList.add(new GuiButton(i * 2, panelLeft + 64, rowY(i), 20, 20, left));
            buttonList.add(new GuiButton(i * 2 + 1, panelLeft + 236, rowY(i), 20, 20, right));
        }
        int fieldY = rowY(rows.size()) + 2;
        targetField = new GuiTextField(fontRendererObj, panelLeft + 64, fieldY, 96, 16);
        targetField.setText(targetText);
        targetField.setMaxStringLength(10);
        buttonList.add(new GuiButton(TREE_BUTTON_ID, panelLeft + 236, fieldY - 2, 60, 20, "Tree..."));
    }

    private String targetText = "64";

    @Override
    public void initGui() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = 12;
        layoutControls();
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == TREE_BUTTON_ID) {
            openTree();
            return;
        }
        int rowIndex = button.id / 2;
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return;
        }
        boolean forward = button.id % 2 == 1;
        int dir = forward ? 1 : -1;
        int step = isShiftKeyDown() ? 10 : 1;
        boolean relayout = false;

        switch (rows.get(rowIndex)) {
            case MACHINE: {
                int idx = presets.indexOf(cfg.preset);
                idx = (idx + dir + presets.size()) % presets.size();
                cfg.preset = presets.get(idx);
                if (cfg.preset.oc == OCKind.HEAT && cfg.machineHeat() < recipe.mSpecialValue) {
                    cfg.fitCoilsTo(recipe.mSpecialValue);
                }
                cfg.clampToPreset();
                relayout = true;
                break;
            }
            case TIER:
                cfg.tier = Math
                    .max(cfg.preset.multiblock ? 0 : minTier, Math.min(GTValues.V.length - 1, cfg.tier + dir));
                break;
            case AMPS:
                cfg.amps = forward ? Math.min(1_048_576, cfg.amps * 2) : Math.max(1, cfg.amps / 2);
                break;
            case COILS:
                cfg.coilOrdinal = Math.max(2, Math.min(HeatingCoilLevel.values().length - 1, cfg.coilOrdinal + dir));
                break;
            case PIPE:
                cfg.pipeTier = Math.max(1, Math.min(4, cfg.pipeTier + dir));
                break;
            case CASING:
                cfg.casingTier = Math.max(cfg.preset.casingMin, Math.min(cfg.preset.casingMax, cfg.casingTier + dir));
                break;
            case MACHINES:
                cfg.machines = Math.max(1, Math.min(99999, cfg.machines + dir * step));
                break;
            case OUTPUT:
                if (!outputs.isEmpty()) {
                    targetOutput = (targetOutput + dir + outputs.size()) % outputs.size();
                }
                break;
        }
        recompute();
        if (relayout) {
            layoutControls();
        }
    }

    private void openTree() {
        if (outputs.isEmpty()) {
            return;
        }
        double target = parseTarget();
        if (target <= 0) {
            target = 64;
        }
        OutputEntry out = outputs.get(targetOutput);
        mc.displayGuiScreen(new GuiRecipeTree(this, recipeMap, recipe, out, target, cfg.copy()));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (targetField.textboxKeyTyped(typedChar, keyCode)) {
            targetText = targetField.getText();
        }
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
            return Double.parseDouble(
                targetField.getText()
                    .trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String fmt(double v) {
        if (v >= 100) return String.format("%,.0f", v);
        if (v >= 1) return String.format("%.2f", v);
        return String.format("%.3f", v);
    }

    private String rowValue(RowKind kind) {
        switch (kind) {
            case MACHINE:
                return cfg.preset.name;
            case TIER:
                return GTValues.VN[cfg.tier] + " (" + String.format("%,d", GTValues.V[cfg.tier]) + " EU/t)";
            case AMPS:
                return cfg.amps + "A";
            case COILS:
                return cfg.coilName() + " (" + String.format("%,d", cfg.coilHeat()) + "K)";
            case PIPE:
                return cfg.pipeCasingName();
            case CASING:
                return cfg.preset.casingName(cfg.casingTier);
            case MACHINES:
                return String.valueOf(cfg.machines);
            case OUTPUT:
                return outputs.isEmpty() ? "-" : trim(outputs.get(targetOutput).name, 18);
        }
        return "";
    }

    private String rowLabel(RowKind kind) {
        switch (kind) {
            case MACHINE:
                return "Machine";
            case TIER:
                return "Tier";
            case AMPS:
                return "Amps";
            case COILS:
                return "Coils";
            case PIPE:
                return "Pipe casing";
            case CASING:
                return cfg.preset.casingLabel;
            case MACHINES:
                return "Machines";
            case OUTPUT:
                return "Output";
        }
        return "";
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        String machineName = StatCollector.translateToLocal(recipeMap.unlocalizedName);
        String title = EnumChatFormatting.GOLD + machineName + EnumChatFormatting.RESET + " - Rate Calculator";
        drawCenteredString(fontRendererObj, title, width / 2, panelTop, 0xFFFFFF);

        for (int i = 0; i < rows.size(); i++) {
            RowKind kind = rows.get(i);
            fontRendererObj.drawString(rowLabel(kind), panelLeft, rowY(i) + 6, 0xAAAAAA);
            drawCenteredString(fontRendererObj, rowValue(kind), panelLeft + 160, rowY(i) + 6, 0xFFFFFF);
        }

        int fieldY = rowY(rows.size()) + 2;
        fontRendererObj.drawString("Target/min", panelLeft, fieldY + 4, 0xAAAAAA);
        targetField.drawTextBox();

        int y = fieldY + 24;

        if (cfg.preset.note != null) {
            fontRendererObj.drawString(EnumChatFormatting.GRAY + cfg.preset.note, panelLeft, y, 0xFFFFFF);
            y += 11;
        }

        if (!rr.ok) {
            fontRendererObj.drawString(EnumChatFormatting.RED + rr.error, panelLeft, y, 0xFFFFFF);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        double craftsPerMin = rr.craftsPerMin;
        String parallelNote = rr.parallels > 1
            ? String.format("   Parallels: %s%d%s", EnumChatFormatting.AQUA, rr.parallels, EnumChatFormatting.RESET)
            : "";
        fontRendererObj.drawString(
            String.format(
                "Time/craft: %s%.2fs%s   EU/t: %s%,d%s%s",
                EnumChatFormatting.AQUA,
                rr.durationTicks / 20.0,
                EnumChatFormatting.RESET,
                EnumChatFormatting.AQUA,
                rr.eut,
                EnumChatFormatting.RESET,
                parallelNote),
            panelLeft,
            y,
            0xFFFFFF);
        y += 11;
        fontRendererObj.drawString(
            String.format("Crafts/min: %s per machine, %s total", fmt(craftsPerMin), fmt(craftsPerMin * cfg.machines)),
            panelLeft,
            y,
            0xFFFFFF);
        y += 14;

        fontRendererObj.drawString(EnumChatFormatting.GREEN + "Outputs (/min, all machines):", panelLeft, y, 0xFFFFFF);
        y += 11;
        for (OutputEntry out : outputs) {
            String chanceNote = out.chance < 10000 ? String.format(" (%.1f%%)", out.chance / 100.0) : "";
            String unit = out.isFluid() ? " L" : "";
            fontRendererObj.drawString(
                "  " + trim(out.name, 26)
                    + chanceNote
                    + ": "
                    + EnumChatFormatting.GREEN
                    + fmt(out.amountPerCraft * craftsPerMin * cfg.machines)
                    + unit,
                panelLeft,
                y,
                0xFFFFFF);
            y += 10;
        }
        y += 4;

        fontRendererObj.drawString(EnumChatFormatting.RED + "Inputs (/min, all machines):", panelLeft, y, 0xFFFFFF);
        y += 11;
        for (InputEntry in : inputs) {
            String unit = in.fluid ? " L" : "";
            fontRendererObj.drawString(
                "  " + trim(in.name, 26)
                    + ": "
                    + EnumChatFormatting.RED
                    + fmt(in.amountPerCraft * craftsPerMin * cfg.machines)
                    + unit,
                panelLeft,
                y,
                0xFFFFFF);
            y += 10;
        }
        y += 6;

        double target = parseTarget();
        if (target > 0 && !outputs.isEmpty()) {
            OutputEntry out = outputs.get(targetOutput);
            double perMachine = out.amountPerCraft * craftsPerMin;
            if (perMachine > 0) {
                int needed = (int) Math.ceil(target / perMachine);
                fontRendererObj.drawString(
                    String.format(
                        "%s%d machines%s (%s, %s) for %s %s/min",
                        EnumChatFormatting.GOLD,
                        needed,
                        EnumChatFormatting.RESET,
                        cfg.preset.name,
                        GTValues.VN[cfg.tier],
                        fmt(target),
                        trim(out.name, 20)),
                    panelLeft,
                    y,
                    0xFFFFFF);
                y += 12;
            }
        }

        if (recipe.mSpecialValue > 0 && cfg.preset.oc == OCKind.HEAT) {
            fontRendererObj.drawString(
                EnumChatFormatting.YELLOW
                    + String.format("Recipe heat: %,dK / machine: %,dK", recipe.mSpecialValue, cfg.machineHeat()),
                panelLeft,
                y,
                0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
