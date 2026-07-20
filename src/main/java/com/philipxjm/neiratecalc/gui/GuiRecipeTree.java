package com.philipxjm.neiratecalc.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.philipxjm.neiratecalc.calc.BookmarkHelper;
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

public class GuiRecipeTree extends GuiScreen {

    private static final int ROW_HEIGHT = 11;
    private static final int LIST_TOP = 34;
    private static final int PANEL_HEIGHT = 66;

    private static class Node {

        final String label;
        final ItemStack stack;
        final FluidStack fluid;
        final Node parent;
        final int depth;

        double requiredPerMin;
        List<RecipeIndex.Producer> producers = new ArrayList<RecipeIndex.Producer>();
        int producerIdx = -1;

        int bookmarkIdx = -1;
        MachineConfig cfg;
        RateResult rr;
        double craftsPerMinNeeded;
        double machinesNeeded;
        final List<Node> children = new ArrayList<Node>();
        boolean expanded;
        boolean loop;

        Node(String label, ItemStack stack, FluidStack fluid, Node parent) {
            this.label = label;
            this.stack = stack;
            this.fluid = fluid;
            this.parent = parent;
            this.depth = parent == null ? 0 : parent.depth + 1;
        }

        boolean sameTarget(Node other) {
            if (stack != null && other.stack != null) {
                return RecipeIndex.itemKey(stack) == RecipeIndex.itemKey(other.stack);
            }
            if (fluid != null && other.fluid != null) {
                return fluid.getFluid() == other.fluid.getFluid();
            }
            return false;
        }

        GTRecipe recipe() {
            return producerIdx >= 0 && producerIdx < producers.size() ? producers.get(producerIdx).recipe : null;
        }

        RecipeMap<?> map() {
            return producerIdx >= 0 && producerIdx < producers.size() ? producers.get(producerIdx).map : null;
        }

        boolean isRaw() {
            return !expanded || recipe() == null;
        }
    }

    private final GuiScreen parent;
    private Node root;
    private GuiTextField targetField;
    private String targetText;

    private ItemStack pendingRootStack;
    private FluidStack pendingRootFluid;

    private final BookmarkHelper.Scope scope;

    private final List<Node> visible = new ArrayList<Node>();
    private int scroll;
    private Node selected;
    private boolean totalsView;
    private boolean indexSeen;
    /** When true, the input field is a crafter count, not a rate. */
    private boolean crafterMode;

    public GuiRecipeTree(GuiScreen parent, RecipeMap<?> recipeMap, GTRecipe recipe, GuiRateCalculator.OutputEntry out,
        double targetPerMin, MachineConfig rootCfg) {
        this.parent = parent;
        this.scope = null;
        this.targetText = GuiRateCalculator.plainNum(targetPerMin);

        root = new Node(out.name, out.stack, out.fluid, null);
        root.requiredPerMin = targetPerMin;
        root.cfg = rootCfg;

        List<RecipeIndex.Producer> known = out.stack != null ? RecipeIndex.forItem(out.stack)
            : RecipeIndex.forFluid(out.fluid);
        for (RecipeIndex.Producer p : known) {
            root.producers.add(p);
            if (p.recipe == recipe) {
                root.producerIdx = root.producers.size() - 1;
            }
        }
        if (root.producerIdx < 0) {
            root.producers.add(0, new RecipeIndex.Producer(recipeMap, recipe));
            root.producerIdx = 0;
        }
        root.bookmarkIdx = BookmarkHelper.findBookmarked(out.stack, out.fluid, root.producers);
        expandChain(root, 0);
        recomputeAll();
        selected = root;
    }

    public GuiRecipeTree(GuiScreen parent, ItemStack stack, FluidStack fluidAlt, double targetPerMin,
        BookmarkHelper.Scope scope) {
        this.parent = parent;
        this.scope = scope;
        this.targetText = GuiRateCalculator.plainNum(targetPerMin);
        this.pendingRootStack = stack;
        this.pendingRootFluid = fluidAlt;
        RecipeIndex.ensureBuilt();
        this.root = buildRootFromTarget();
        recomputeAll();
        selected = root;
    }

    private Node buildRootFromTarget() {
        List<RecipeIndex.Producer> itemProducers = pendingRootStack != null ? RecipeIndex.forItem(pendingRootStack)
            : java.util.Collections.<RecipeIndex.Producer>emptyList();
        Node n;
        if (!itemProducers.isEmpty() || pendingRootFluid == null) {
            String label = pendingRootStack != null ? pendingRootStack.getDisplayName() : "?";
            n = new Node(label, pendingRootStack, null, null);
            n.producers = new ArrayList<RecipeIndex.Producer>(itemProducers);
        } else {
            n = new Node(pendingRootFluid.getLocalizedName(), null, pendingRootFluid, null);
            n.producers = new ArrayList<RecipeIndex.Producer>(RecipeIndex.forFluid(pendingRootFluid));
        }
        n.requiredPerMin = Math.max(0, parseTarget());
        n.bookmarkIdx = BookmarkHelper.findBookmarked(n.stack, n.fluid, n.producers, scope);
        if (!n.producers.isEmpty()) {
            n.producerIdx = n.bookmarkIdx >= 0 ? n.bookmarkIdx : 0;
            n.cfg = MachineRegistry.defaultConfig(n.map().unlocalizedName, n.recipe());
            expandChain(n, 0);
        }
        return n;
    }

    private void expandChain(Node node, int depth) {
        if (!node.expanded) {
            expand(node);
        }
        if (depth > 10) {
            return;
        }
        for (Node child : node.children) {
            if (child.loop || child.expanded || child.bookmarkIdx < 0) {
                continue;
            }
            child.producerIdx = child.bookmarkIdx;
            GTRecipe recipe = child.recipe();
            if (recipe == null) {
                continue;
            }
            child.cfg = MachineRegistry.defaultConfig(child.map().unlocalizedName, recipe);
            expandChain(child, depth + 1);
        }
    }

    private void expand(Node node) {
        GTRecipe recipe = node.recipe();
        if (recipe == null || node.loop) {
            return;
        }
        node.expanded = true;
        rebuildChildren(node);
    }

    private void rebuildChildren(Node node) {
        node.children.clear();
        GTRecipe recipe = node.recipe();
        if (recipe == null) {
            return;
        }
        if (recipe.mInputs != null) {
            for (ItemStack in : recipe.mInputs) {
                if (in == null || in.stackSize <= 0 || in.getItem() == null) continue;
                Node child = new Node(in.getDisplayName(), in, null, node);
                child.producers = new ArrayList<RecipeIndex.Producer>(RecipeIndex.forItem(in));
                child.bookmarkIdx = BookmarkHelper.findBookmarked(in, null, child.producers, scope);
                markLoop(child);
                node.children.add(child);
            }
        }
        if (recipe.mFluidInputs != null) {
            for (FluidStack in : recipe.mFluidInputs) {
                if (in == null || in.amount <= 0 || in.getFluid() == null) continue;
                Node child = new Node(in.getLocalizedName(), null, in, node);
                child.producers = new ArrayList<RecipeIndex.Producer>(RecipeIndex.forFluid(in));
                child.bookmarkIdx = BookmarkHelper.findBookmarked(null, in, child.producers, scope);
                markLoop(child);
                node.children.add(child);
            }
        }
    }

    private static void markLoop(Node child) {
        for (Node up = child.parent; up != null; up = up.parent) {
            if (child.sameTarget(up)) {
                child.loop = true;
                return;
            }
        }
    }

    private static double outputPerCraft(Node node) {
        GTRecipe recipe = node.recipe();
        if (recipe == null) {
            return 1;
        }
        double amount = 0;
        if (node.stack != null && recipe.mOutputs != null) {
            long key = RecipeIndex.itemKey(node.stack);
            for (int i = 0; i < recipe.mOutputs.length; i++) {
                ItemStack out = recipe.mOutputs[i];
                if (out == null || out.getItem() == null || RecipeIndex.itemKey(out) != key) continue;
                int chance = 10000;
                if (recipe.mOutputChances != null && i < recipe.mOutputChances.length) {
                    chance = recipe.mOutputChances[i];
                }
                amount += out.stackSize * (chance / 10000.0);
            }
        }
        if (node.fluid != null && recipe.mFluidOutputs != null) {
            for (FluidStack out : recipe.mFluidOutputs) {
                if (out == null || out.getFluid() != node.fluid.getFluid()) continue;
                amount += out.amount;
            }
        }
        return amount > 0 ? amount : 1;
    }

    private void recomputeAll() {
        double input = parseTarget();
        if (input < 0) {
            input = 0;
        }
        if (crafterMode) {
            // A fixed number of crafters drives the rate instead.
            root.requiredPerMin = 0;
            GTRecipe recipe = root.recipe();
            if (recipe != null && root.cfg != null) {
                RateResult rr = RateMath.compute(recipe, root.cfg);
                if (rr.ok) {
                    root.requiredPerMin = input * rr.craftsPerMin * outputPerCraft(root);
                }
            }
        } else {
            root.requiredPerMin = input;
        }
        recomputeNode(root);
        rebuildVisible();
    }

    private void recomputeNode(Node node) {
        GTRecipe recipe = node.recipe();
        if (recipe == null) {
            return;
        }
        if (node.cfg == null) {
            node.cfg = MachineRegistry.defaultConfig(node.map().unlocalizedName, recipe);
        }
        node.craftsPerMinNeeded = node.requiredPerMin / outputPerCraft(node);
        node.rr = RateMath.compute(recipe, node.cfg);
        node.machinesNeeded = node.rr.ok && node.rr.craftsPerMin > 0 ? node.craftsPerMinNeeded / node.rr.craftsPerMin
            : 0;

        if (!node.expanded) {
            return;
        }

        for (Node child : node.children) {
            double perCraft = child.stack != null ? child.stack.stackSize : child.fluid.amount;
            child.requiredPerMin = perCraft * node.craftsPerMinNeeded;
            recomputeNode(child);
        }
    }

    private void rebuildVisible() {
        visible.clear();
        addVisible(root);
        if (scroll > Math.max(0, visible.size() - visibleRows())) {
            scroll = Math.max(0, visible.size() - visibleRows());
        }
    }

    private void addVisible(Node node) {
        visible.add(node);
        if (node.expanded) {
            for (Node child : node.children) {
                addVisible(child);
            }
        }
    }

    private static class Totals {

        final Map<String, double[]> raw = new LinkedHashMap<String, double[]>();
        final Map<String, double[]> machines = new LinkedHashMap<String, double[]>();
        double totalEut;
    }

    private Totals computeTotals() {
        Totals t = new Totals();
        collectTotals(root, t);
        return t;
    }

    private void collectTotals(Node node, Totals t) {
        if (node.isRaw()) {
            if (node != root) {
                double[] entry = t.raw.get(node.label);
                if (entry == null) {
                    entry = new double[] { 0, node.fluid != null ? 1 : 0 };
                    t.raw.put(node.label, entry);
                }
                entry[0] += node.requiredPerMin;
            }
            return;
        }
        if (node.rr != null && node.rr.ok) {
            t.totalEut += node.machinesNeeded * node.rr.eut;
            String label = node.cfg.preset.name + " (" + GTValues.VN[node.cfg.tier] + ")";
            double[] entry = t.machines.get(label);
            if (entry == null) {
                entry = new double[] { 0 };
                t.machines.put(label, entry);
            }
            entry[0] += node.machinesNeeded;
        }
        for (Node child : node.children) {
            collectTotals(child, t);
        }
    }

    private int visibleRows() {
        return Math.max(1, (height - PANEL_HEIGHT - LIST_TOP - 4) / ROW_HEIGHT);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int py = height - PANEL_HEIGHT + 22;
        int py2 = py + 22;

        buttonList.add(new GuiButton(1, 8, py, 14, 20, "<"));
        buttonList.add(new GuiButton(2, 92, py, 14, 20, ">"));
        buttonList.add(new GuiButton(3, 112, py, 14, 20, "<"));
        buttonList.add(new GuiButton(4, width - 166, py, 14, 20, ">"));
        buttonList.add(new GuiButton(5, width - 146, py, 14, 20, "<"));
        buttonList.add(new GuiButton(6, width - 22, py, 14, 20, ">"));

        buttonList.add(new GuiButton(7, 8, py2, 14, 20, "<"));
        buttonList.add(new GuiButton(8, 66, py2, 14, 20, ">"));
        buttonList.add(new GuiButton(9, 86, py2, 14, 20, "<"));
        buttonList.add(new GuiButton(10, width - 186, py2, 14, 20, ">"));
        buttonList.add(new GuiButton(11, width - 166, py2, 14, 20, "<"));
        buttonList.add(new GuiButton(12, width - 22, py2, 14, 20, ">"));
        buttonList.add(new GuiButton(22, width - 172, 6, 48, 20, "Cfg"));
        buttonList.add(new GuiButton(20, width - 120, 6, 54, 20, "Totals"));
        buttonList.add(new GuiButton(21, width - 62, 6, 54, 20, "Back"));

        buttonList.add(new GuiButton(23, 144, 6, 60, 20, crafterMode ? "crafters" : "/min"));
        targetField = new GuiTextField(fontRendererObj, 70, 8, 70, 16);
        targetField.setText(targetText);
        targetField.setMaxStringLength(10);
        Keyboard.enableRepeatEvents(true);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 20) {
            totalsView = !totalsView;
            button.displayString = totalsView ? "Tree" : "Totals";
            scroll = 0;
            return;
        }
        if (button.id == 21) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 22) {
            mc.displayGuiScreen(new GuiRateCalcSettings(this));
            return;
        }
        if (button.id == 23) {
            // Convert the field so the plan stays the same across the toggle.
            crafterMode = !crafterMode;
            button.displayString = crafterMode ? "crafters" : "/min";
            double converted = crafterMode ? root.machinesNeeded : root.requiredPerMin;
            targetText = GuiRateCalculator.plainNum(Math.max(0, converted));
            targetField.setText(targetText);
            recomputeAll();
            return;
        }
        Node node = selected;
        if (node == null) {
            return;
        }
        switch (button.id) {
            case 1:
            case 2:
                cycleRecipe(node, button.id == 1 ? -1 : 1);
                break;
            case 3:
            case 4:
                cycleMachine(node, button.id == 3 ? -1 : 1);
                break;
            case 5:
            case 6:
                if (node.cfg != null) {
                    int minTier = node.cfg.preset.multiblock ? 0 : MachineConfig.minTierFor(nodeRecipeEUt(node));
                    node.cfg.tier = Math
                        .max(minTier, Math.min(GTValues.V.length - 1, node.cfg.tier + (button.id == 5 ? -1 : 1)));
                }
                break;
            case 7:
            case 8:
                if (node.cfg != null && node.cfg.preset.multiblock) {
                    node.cfg.amps = button.id == 8 ? Math.min(1_048_576, node.cfg.amps * 2)
                        : Math.max(1, node.cfg.amps / 2);
                }
                break;
            case 9:
            case 10:
                if (node.cfg != null && node.cfg.preset.usesCoils) {
                    node.cfg.coilOrdinal = Math.max(
                        2,
                        Math.min(
                            HeatingCoilLevel.values().length - 1,
                            node.cfg.coilOrdinal + (button.id == 9 ? -1 : 1)));
                }
                break;
            case 11:
            case 12: {
                int dir = button.id == 11 ? -1 : 1;
                if (node.cfg != null && node.cfg.preset.usesPipeCasing) {
                    node.cfg.pipeTier = Math.max(1, Math.min(4, node.cfg.pipeTier + dir));
                } else if (node.cfg != null && node.cfg.preset.casingLabel != null) {
                    node.cfg.casingTier = Math
                        .max(node.cfg.preset.casingMin, Math.min(node.cfg.preset.casingMax, node.cfg.casingTier + dir));
                }
                break;
            }
        }
        if (node.cfg != null && node.map() != null) {
            MachineRegistry.rememberChoice(node.map().unlocalizedName, node.cfg);
        }
        recomputeAll();
    }

    private static long nodeRecipeEUt(Node node) {
        GTRecipe recipe = node.recipe();
        return recipe != null ? recipe.mEUt : 0;
    }

    private void cycleRecipe(Node node, int dir) {
        if (node.producers.size() < 2) {
            return;
        }
        node.producerIdx = (node.producerIdx + dir + node.producers.size()) % node.producers.size();
        node.cfg = null;
        if (node.expanded) {
            rebuildChildren(node);
        }
        GTRecipe recipe = node.recipe();
        if (recipe != null) {
            node.cfg = MachineRegistry.defaultConfig(node.map().unlocalizedName, recipe);
        }
    }

    private void cycleMachine(Node node, int dir) {
        if (node.cfg == null || node.map() == null) {
            return;
        }
        List<MachinePreset> presets = MachineRegistry.presetsFor(node.map().unlocalizedName);
        int idx = presets.indexOf(node.cfg.preset);
        idx = (idx + dir + presets.size()) % presets.size();
        node.cfg.preset = presets.get(idx);
        GTRecipe recipe = node.recipe();
        if (recipe != null && node.cfg.preset.oc == OCKind.HEAT && node.cfg.machineHeat() < recipe.mSpecialValue) {
            node.cfg.fitCoilsTo(recipe.mSpecialValue);
        }
        node.cfg.clampToPreset();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (targetField.textboxKeyTyped(typedChar, keyCode)) {
            targetText = targetField.getText();
            recomputeAll();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        targetField.mouseClicked(mouseX, mouseY, button);
        if (totalsView || mouseY < LIST_TOP || mouseY >= height - PANEL_HEIGHT) {
            return;
        }
        int row = scroll + (mouseY - LIST_TOP) / ROW_HEIGHT;
        if (row < 0 || row >= visible.size()) {
            return;
        }
        Node node = visible.get(row);
        selected = node;
        int expanderX = 6 + node.depth * 10;
        if (mouseX >= expanderX && mouseX < expanderX + 10) {
            toggleExpand(node);
        }
        recomputeAll();
    }

    private void toggleExpand(Node node) {
        if (node.expanded) {
            node.expanded = false;
            return;
        }
        if (node.loop) {
            return;
        }
        if (node.producers.isEmpty() && RecipeIndex.isReady()) {

            return;
        }
        if (!RecipeIndex.isReady()) {
            return;
        }
        if (node.producerIdx < 0) {
            node.producerIdx = node.bookmarkIdx >= 0 ? node.bookmarkIdx : 0;
            GTRecipe recipe = node.recipe();
            if (recipe != null) {
                node.cfg = MachineRegistry.defaultConfig(node.map().unlocalizedName, recipe);
            }
        }
        expandChain(node, 0);
    }

    @Override
    public void updateScreen() {
        targetField.updateCursorCounter();

        if (!indexSeen && RecipeIndex.isReady()) {
            indexSeen = true;
            if (root.producerIdx < 0 && (pendingRootStack != null || pendingRootFluid != null)) {
                root = buildRootFromTarget();
                selected = root;
            } else {
                refreshProducers(root);

                expandChain(root, 0);
            }
            recomputeAll();
        }
    }

    private void refreshProducers(Node node) {
        if (node.producers.isEmpty()) {
            if (node.stack != null) {
                node.producers = new ArrayList<RecipeIndex.Producer>(RecipeIndex.forItem(node.stack));
            } else if (node.fluid != null) {
                node.producers = new ArrayList<RecipeIndex.Producer>(RecipeIndex.forFluid(node.fluid));
            }
            node.bookmarkIdx = BookmarkHelper.findBookmarked(node.stack, node.fluid, node.producers, scope);
        }
        for (Node child : node.children) {
            refreshProducers(child);
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll += wheel > 0 ? -3 : 3;
            scroll = Math.max(0, Math.min(Math.max(0, visible.size() - visibleRows()), scroll));
        }
    }

    private double parseTarget() {
        return GuiRateCalculator.parseFlexible(targetText);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        fontRendererObj.drawString(crafterMode ? "Crafters" : "Target/min", 8, 12, 0xAAAAAA);
        targetField.drawTextBox();
        String derived = crafterMode
            ? "= " + GuiRateCalculator.fmt(root.requiredPerMin) + (root.fluid != null ? " L/min" : "/min")
            : "= " + GuiRateCalculator.fmt(root.machinesNeeded) + " crafters";
        fontRendererObj.drawString(EnumChatFormatting.AQUA + derived, 210, 12, 0xFFFFFF);

        if (!RecipeIndex.isReady()) {
            drawCenteredString(
                fontRendererObj,
                EnumChatFormatting.YELLOW + "Indexing recipes... inputs expand once done",
                width / 2,
                LIST_TOP - 11,
                0xFFFFFF);
        }

        if (totalsView) {
            drawTotals();
        } else {
            drawTree(mouseY);
        }

        drawControlPanel();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTree(int mouseY) {
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            if (idx >= visible.size()) break;
            Node node = visible.get(idx);
            int y = LIST_TOP + i * ROW_HEIGHT;
            if (node == selected) {
                drawRect(4, y - 1, width - 4, y + ROW_HEIGHT - 1, 0x40FFFFFF);
            }
            int x = 6 + node.depth * 10;

            String expander;
            if (node.loop) {
                expander = EnumChatFormatting.RED + "x";
            } else if (node.expanded) {
                expander = "-";
            } else if (node.producers.isEmpty() && RecipeIndex.isReady()) {
                expander = EnumChatFormatting.DARK_GRAY + ".";
            } else {
                expander = "+";
            }
            fontRendererObj.drawString(expander, x, y, 0xFFFFFF);

            String unit = node.fluid != null ? " L/min" : "/min";
            String left = GuiRateCalculator.fmt(node.requiredPerMin) + unit
                + " "
                + EnumChatFormatting.WHITE
                + GuiRateCalculator.trim(node.label, 24);
            int leftColor = node.loop ? 0xFF5555 : node.isRaw() ? 0xFFD37F : 0xA0A0A0;
            fontRendererObj.drawString(left, x + 10, y, leftColor);

            String right;
            if (node.loop) {
                right = EnumChatFormatting.RED + "loop";
            } else if (node.isRaw()) {
                if (node.producers.isEmpty() && RecipeIndex.isReady()) {
                    right = EnumChatFormatting.DARK_GRAY + "raw";
                } else if (node.bookmarkIdx >= 0) {
                    right = EnumChatFormatting.GOLD + "* " + EnumChatFormatting.GRAY + "raw (bookmarked, + crafts)";
                } else {
                    right = EnumChatFormatting.GRAY + "raw (click + to craft)";
                }
            } else if (node.rr != null && !node.rr.ok) {
                right = EnumChatFormatting.RED + "power!";
            } else {
                String count = node.machinesNeeded >= 10 ? String.format("%,.0f", Math.ceil(node.machinesNeeded))
                    : String.format("%.1f", node.machinesNeeded);
                String star = node.producerIdx == node.bookmarkIdx && node.bookmarkIdx >= 0
                    ? EnumChatFormatting.GOLD + "* "
                    : "";
                right = star + EnumChatFormatting.AQUA
                    + count
                    + "x "
                    + EnumChatFormatting.RESET
                    + node.cfg.preset.name
                    + " ("
                    + GTValues.VN[node.cfg.tier]
                    + ")";
            }
            fontRendererObj.drawString(right, width - 8 - fontRendererObj.getStringWidth(right), y, 0xFFFFFF);
        }
    }

    private void drawTotals() {
        Totals t = computeTotals();
        int y = LIST_TOP;
        fontRendererObj.drawString(
            EnumChatFormatting.GOLD + String.format("Total power: %,.0f EU/t average", t.totalEut),
            8,
            y,
            0xFFFFFF);
        y += 14;
        fontRendererObj.drawString(EnumChatFormatting.AQUA + "Machines:", 8, y, 0xFFFFFF);
        y += 11;
        for (Map.Entry<String, double[]> e : t.machines.entrySet()) {
            fontRendererObj.drawString(
                String.format("  %.1fx (build %d) %s", e.getValue()[0], (int) Math.ceil(e.getValue()[0]), e.getKey()),
                8,
                y,
                0xFFFFFF);
            y += 10;
            if (y > height - PANEL_HEIGHT - 12) return;
        }
        y += 6;
        fontRendererObj.drawString(EnumChatFormatting.YELLOW + "Raw inputs (/min):", 8, y, 0xFFFFFF);
        y += 11;
        for (Map.Entry<String, double[]> e : t.raw.entrySet()) {
            String unit = e.getValue()[1] > 0 ? " L" : "";
            fontRendererObj.drawString(
                "  " + GuiRateCalculator.trim(e.getKey(), 30)
                    + ": "
                    + EnumChatFormatting.YELLOW
                    + GuiRateCalculator.fmt(e.getValue()[0])
                    + unit,
                8,
                y,
                0xFFFFFF);
            y += 10;
            if (y > height - PANEL_HEIGHT - 12) return;
        }
    }

    private void drawControlPanel() {
        int py = height - PANEL_HEIGHT;
        drawRect(0, py, width, height, 0xC0101010);
        Node node = selected;
        if (node == null) {
            return;
        }

        String header;
        if (node.recipe() == null) {
            header = EnumChatFormatting.GRAY + node.label + " - no recipe selected";
        } else {
            String recipePos = (node.producerIdx + 1) + "/" + node.producers.size();
            if (node.producerIdx == node.bookmarkIdx && node.bookmarkIdx >= 0) {
                recipePos += EnumChatFormatting.GOLD + "*" + EnumChatFormatting.RESET;
            }
            String power = node.rr != null && node.rr.ok
                ? String.format("%,d EU/t, %.2fs, %d par", node.rr.eut, node.rr.durationTicks / 20.0, node.rr.parallels)
                : (node.rr != null ? node.rr.error : "");
            header = EnumChatFormatting.GOLD + GuiRateCalculator.trim(node.label, 20)
                + EnumChatFormatting.RESET
                + "  recipe "
                + recipePos
                + "  "
                + EnumChatFormatting.AQUA
                + power;
        }
        fontRendererObj.drawString(header, 8, py + 8, 0xFFFFFF);

        int ty = py + 22 + 6;
        MachineConfig cfg = node.cfg;

        int machineCenter = (132 + width - 166) / 2;
        int machineChars = Math.max(16, (width - 166 - 132) / 6);
        drawCenteredString(fontRendererObj, "recipe", 57, ty, 0xAAAAAA);
        drawCenteredString(
            fontRendererObj,
            cfg != null ? GuiRateCalculator.trim(cfg.preset.name, machineChars) : "-",
            machineCenter,
            ty,
            0xFFFFFF);
        drawCenteredString(fontRendererObj, cfg != null ? GTValues.VN[cfg.tier] : "-", width - 75, ty, 0xFFFFFF);

        int ty2 = ty + 22;
        int coilCenter = (106 + width - 186) / 2;
        int coilChars = Math.max(16, (width - 186 - 106) / 6);
        drawCenteredString(
            fontRendererObj,
            cfg != null && cfg.preset.multiblock ? cfg.amps + "A" : "-",
            44,
            ty2,
            0xFFFFFF);
        drawCenteredString(
            fontRendererObj,
            cfg != null && cfg.preset.usesCoils ? GuiRateCalculator.trim(cfg.coilName(), coilChars) : "-",
            coilCenter,
            ty2,
            0xFFFFFF);
        String extra = "-";
        if (cfg != null && cfg.preset.usesPipeCasing) {
            extra = "pipe: " + cfg.pipeCasingName();
        } else if (cfg != null && cfg.preset.casingLabel != null) {
            extra = GuiRateCalculator.trim(cfg.preset.casingLabel + ": " + cfg.preset.casingName(cfg.casingTier), 22);
        }
        drawCenteredString(fontRendererObj, extra, width - 94, ty2, 0xFFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
