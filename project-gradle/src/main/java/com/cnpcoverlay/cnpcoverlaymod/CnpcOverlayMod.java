package com.cnpcoverlay.cnpcoverlaymod;

import com.cnpcoverlay.cnpcoverlaymod.common.network.CnpcNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.slf4j.Logger;

@Mod(CnpcOverlayMod.MODID)
public final class CnpcOverlayMod {
    public static final String MODID = "cnpcoverlay";

    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcOverlayMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        CnpcNetwork.register();
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
                com.cnpcoverlay.cnpcoverlaymod.client.CnpcOverlayClient.register(modEventBus));

        LOGGER.info("CNPC Overlay initialised");
    }
}
