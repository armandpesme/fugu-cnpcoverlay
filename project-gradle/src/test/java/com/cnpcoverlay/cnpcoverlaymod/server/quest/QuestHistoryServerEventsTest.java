package com.cnpcoverlay.cnpcoverlaymod.server.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestHistoryServerEventsTest {

    @Test
    void readsTheDisplayNameFromTheCustomNpcsCategory() {
        assertEquals("Aventures", QuestHistoryServerEvents.categoryName(new TestCategory()));
    }

    public static final class TestCategory {
        public String getName() {
            return "Aventures";
        }

        @Override
        public String toString() {
            return "noppes.npcs.controllers.data.QuestCategory@bad";
        }
    }
}
