package com.cnpcoverlay.cnpcoverlaymod.client.integration;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestSnapshot;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;

public interface QuestProvider {
    boolean isAvailable();

    List<QuestSnapshot> getActiveQuests(Player player);

    Object getFinishedQuestIdentity(Player player);

    FinishedQuestStamps getFinishedQuestStamps(Player player);

    record FinishedQuestStamps(Object identityToken, Map<String, Long> stamps, boolean available) {
        public FinishedQuestStamps(Object identityToken, Map<String, Long> stamps) {
            this(identityToken, stamps, true);
        }

        public FinishedQuestStamps {
            identityToken = identityToken == null ? FinishedQuestStamps.class : identityToken;
            stamps = Map.copyOf(stamps);
        }

        public static FinishedQuestStamps empty() {
            return new FinishedQuestStamps(FinishedQuestStamps.class, Map.of());
        }

        public static FinishedQuestStamps unavailable() {
            return new FinishedQuestStamps(FinishedQuestStamps.class, Map.of(), false);
        }
    }
}
