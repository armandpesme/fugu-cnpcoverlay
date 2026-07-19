package com.cnpcoverlay.cnpcoverlaymod.client;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

public final class CnpcOverlayClient {
    private CnpcOverlayClient() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CnpcOverlayClient::onRegisterGuiOverlays);
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("cnpc_overlay", CnpcOverlayHud.OVERLAY);
    }
}
