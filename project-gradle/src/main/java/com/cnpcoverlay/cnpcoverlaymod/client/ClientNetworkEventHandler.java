package com.cnpcoverlay.cnpcoverlaymod.client;

import com.cnpcoverlay.cnpcoverlaymod.CnpcOverlayMod;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestTrackerState;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Écoute les événements réseau côté client pour nettoyer les marqueurs
 * JourneyMap lors de la déconnexion ou du changement de serveur.
 */
@Mod.EventBusSubscriber(modid = CnpcOverlayMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientNetworkEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientNetworkEventHandler() {
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        LOGGER.debug("Déconnexion client — nettoyage des marqueurs JourneyMap");
        QuestTrackerState.get().clearForDisconnect();
    }
}
