package com.cnpcoverlay.cnpcoverlaymod;

import com.cnpcoverlay.cnpcoverlaymod.client.CnpcOverlayClient;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CnpcOverlayMod.MODID)
public final class CnpcOverlayMod {
    public static final String MODID = "cnpcoverlay";

    private static final Logger LOGGER = LogUtils.getLogger();

    public CnpcOverlayMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        CnpcOverlayClient.register(modEventBus);

        LOGGER.info("CNPC Overlay initialised");
    }
}
