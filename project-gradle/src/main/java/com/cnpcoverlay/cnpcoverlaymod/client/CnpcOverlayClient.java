package com.cnpcoverlay.cnpcoverlaymod.client;

import com.cnpcoverlay.cnpcoverlaymod.CnpcOverlayMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CnpcOverlayMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CnpcOverlayClient {
    private CnpcOverlayClient() {
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cnpc_overlay", CnpcOverlayHud.OVERLAY);
    }
}
