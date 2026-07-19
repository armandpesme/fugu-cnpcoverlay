package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap.QuestMapMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuestMarkerPlannerTest {
    private final QuestMarkerPlanner planner = new QuestMarkerPlanner();

    @Test
    void plansOnlyIncompleteObjectives() {
        QuestSnapshot quest = new QuestSnapshot("42", "Aventures", "Les bandits", "#!1-10,20,30,5#!2-40,50,60,7",
                List.of(new QuestObjectiveSnapshot(1, "Défaire le chef", 1, 1, true),
                        new QuestObjectiveSnapshot(2, "Trouver le repaire", 0, 1, false)),
                0.5f, false, "");

        var markers = planner.plan(quest, QuestMapMetadata.parse(quest.rawLogText()), true, "minecraft:overworld", "ctx", "player");

        assertEquals(2, markers.size(), "un point et son cercle pour le seul objectif inachevé");
        assertTrue(markers.stream().allMatch(marker -> marker.objectiveIndex() == 2));
    }

    @Test
    void plansOnlyTurnInWhenQuestIsCompleted() {
        QuestSnapshot quest = new QuestSnapshot("42", "Aventures", "Les bandits", "#!1-10,20,30,5#?-40,50,60",
                List.of(new QuestObjectiveSnapshot(1, "Défaire le chef", 1, 1, true)),
                1.0f, true, "Capitaine");

        var markers = planner.plan(quest, QuestMapMetadata.parse(quest.rawLogText()), true, "minecraft:overworld", "ctx", "player");

        assertEquals(1, markers.size());
        assertEquals(QuestMarkerPlanner.MarkerType.TURN_IN, markers.get(0).type());
    }

    @Test
    void ignoresUnfollowedQuestsAndInvalidObjectiveIndexes() {
        QuestSnapshot quest = new QuestSnapshot("42", "Aventures", "Les bandits", "#!2-10,20,30,5",
                List.of(new QuestObjectiveSnapshot(1, "Défaire le chef", 0, 1, false)),
                0.0f, false, "");

        assertTrue(planner.plan(quest, QuestMapMetadata.parse(quest.rawLogText()), false, "minecraft:overworld", "ctx", "player").isEmpty());
        assertTrue(planner.plan(quest, QuestMapMetadata.parse(quest.rawLogText()), true, "minecraft:overworld", "ctx", "player").isEmpty());
    }
}
