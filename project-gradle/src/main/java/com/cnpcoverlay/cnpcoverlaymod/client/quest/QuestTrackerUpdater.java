package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import com.cnpcoverlay.cnpcoverlaymod.CnpcOverlayMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CnpcOverlayMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QuestTrackerUpdater {
    private static int tick = 0;

    private QuestTrackerUpdater() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tick++;
        if (tick % 20 != 0) {
            return;
        }

        var mc = Minecraft.getInstance();
        QuestTrackerState.get().refresh(mc.player);
    }
}
