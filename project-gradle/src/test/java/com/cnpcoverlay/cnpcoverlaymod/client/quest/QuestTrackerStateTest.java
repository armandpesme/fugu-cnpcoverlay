package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class QuestTrackerStateTest {

    @Test
    void newlySeenQuestIdsExcludeQuestsAlreadyKnownToThePlayer() {
        List<QuestSnapshot> snapshots = List.of(
                snapshot("existing"),
                snapshot("new-quest"));

        assertEquals(Set.of("new-quest"), QuestTrackerState.newlySeenQuestIds(Set.of("existing"), snapshots));
    }

    private static QuestSnapshot snapshot(String id) {
        return new QuestSnapshot(id, "category", "title", "", List.of(), 0.0f, false, null);
    }
}
