package com.cnpcoverlay.cnpcoverlaymod;

import com.cnpcoverlay.cnpcoverlaymod.common.network.CnpcNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CnpcOverlayMod.MODID)
public final class CnpcOverlayMod {
    public static final String MODID = "cnpcoverlay";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcOverlayMod(FMLJavaModLoadingContext context) {
        CnpcNetwork.register();

        LOGGER.info("CNPC Overlay initialised");
    }
}
