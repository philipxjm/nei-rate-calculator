package com.philipxjm.neiratecalc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = NEIRateCalc.MODID,
    version = Tags.VERSION,
    name = "NEI Rate Calculator",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:NotEnoughItems;required-after:gregtech")
public class NEIRateCalc {

    public static final String MODID = "neiratecalc";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.philipxjm.neiratecalc.ClientProxy",
        serverSide = "com.philipxjm.neiratecalc.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Config.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
