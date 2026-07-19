package com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap;

import com.cnpcoverlay.cnpcoverlaymod.CnpcOverlayMod;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestMarkerPublisher;
import com.mojang.logging.LogUtils;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import org.slf4j.Logger;

/** Point d'entrée chargé uniquement par JourneyMap lorsque sa vraie API v2 est disponible. */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class CnpcOverlayJourneyMapPlugin implements IClientPlugin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void initialize(IClientAPI api) {
        JourneyMapMarkerManager bridge = JourneyMapMarkerManager.get();
        bridge.initialize(api);
        QuestMarkerPublisher.install(bridge::synchronize);
        LOGGER.info("CNPCoverlay JourneyMap plugin initialisé (API v2)");
    }

    @Override
    public String getModId() {
        return CnpcOverlayMod.MODID;
    }
}
