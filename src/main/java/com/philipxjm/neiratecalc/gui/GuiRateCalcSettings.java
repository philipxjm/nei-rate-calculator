package com.philipxjm.neiratecalc.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import com.philipxjm.neiratecalc.Config;

import gregtech.api.enums.GTValues;

/** In-game editor for the calculator defaults; saves straight to the cfg. */
public class GuiRateCalcSettings extends GuiScreen {

    private static final int PANEL_WIDTH = 280;
    private static final int ROW_HEIGHT = 24;

    private final GuiScreen parent;
    private int panelLeft;
    private int panelTop;

    public GuiRateCalcSettings(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = height / 2 - 70;
        buttonList.clear();
        for (int row = 0; row < 4; row++) {
            int y = rowY(row);
            if (row == 2) {
                // Prefer-multiblock is a single toggle button.
                buttonList.add(new GuiButton(4, panelLeft + 150, y, 106, 20, ""));
            } else {
                buttonList.add(new GuiButton(row * 2, panelLeft + 150, y, 20, 20, "<"));
                buttonList.add(new GuiButton(row * 2 + 1, panelLeft + 236, y, 20, 20, ">"));
            }
        }
        buttonList.add(new GuiButton(99, panelLeft + 150, rowY(4) + 6, 106, 20, "Back"));
    }

    private int rowY(int row) {
        return panelTop + 20 + row * ROW_HEIGHT;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                Config.defaultTier = Math.max(-1, Config.defaultTier - 1);
                break;
            case 1:
                Config.defaultTier = Math.min(GTValues.V.length - 1, Config.defaultTier + 1);
                break;
            case 2:
                Config.defaultAmps = Math.max(1, Config.defaultAmps / 2);
                break;
            case 3:
                Config.defaultAmps = Math.min(1_048_576, Config.defaultAmps * 2);
                break;
            case 4:
                Config.preferMultiblock = !Config.preferMultiblock;
                break;
            case 6:
                Config.maxParallels = Math.max(1, Config.maxParallels / 2);
                break;
            case 7:
                Config.maxParallels = Math.min(4096, Config.maxParallels * 2);
                break;
            case 99:
                mc.displayGuiScreen(parent);
                return;
        }
        Config.save();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            EnumChatFormatting.GOLD + "Rate Calculator Settings",
            width / 2,
            panelTop,
            0xFFFFFF);

        String tierText = Config.defaultTier < 0 ? "Auto (recipe min)"
            : GTValues.VN[Config.defaultTier] + " (" + String.format("%,d", GTValues.V[Config.defaultTier]) + " EU/t)";
        drawRow(0, "Default tier", tierText);
        drawRow(1, "Default amps", Config.defaultAmps + "A");
        drawRow(2, "Prefer multiblocks", null);
        ((GuiButton) buttonList.get(buttonIndexFor(4))).displayString = Config.preferMultiblock ? "On" : "Off";
        drawRow(3, "Max parallels", String.valueOf(Config.maxParallels));

        fontRendererObj.drawString(
            EnumChatFormatting.GRAY + "Applies to newly opened calculations",
            panelLeft,
            rowY(4) + 34,
            0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int buttonIndexFor(int id) {
        for (int i = 0; i < buttonList.size(); i++) {
            if (((GuiButton) buttonList.get(i)).id == id) {
                return i;
            }
        }
        return 0;
    }

    private void drawRow(int row, String label, String value) {
        int y = rowY(row);
        fontRendererObj.drawString(label, panelLeft, y + 6, 0xAAAAAA);
        if (value != null) {
            drawCenteredString(fontRendererObj, value, panelLeft + 203, y + 6, 0xFFFFFF);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
