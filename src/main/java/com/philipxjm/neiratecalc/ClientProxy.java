package com.philipxjm.neiratecalc;

import org.lwjgl.input.Keyboard;

import com.philipxjm.neiratecalc.nei.RateCalcInputHandler;

import codechicken.nei.api.API;
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        API.addHashBind(RateCalcInputHandler.KEY_ID, Keyboard.KEY_K);
        GuiContainerManager.addInputHandler(new RateCalcInputHandler());
        NEIRateCalc.LOG.info("Rate calculator hooked into NEI (default key: K over a GT recipe)");
    }
}
