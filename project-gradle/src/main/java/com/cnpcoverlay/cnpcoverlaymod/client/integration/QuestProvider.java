package com.cnpcoverlay.cnpcoverlaymod.client.integration;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestSnapshot;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public interface QuestProvider {
    boolean isAvailable();

    List<QuestSnapshot> getActiveQuests(Player player);
}
