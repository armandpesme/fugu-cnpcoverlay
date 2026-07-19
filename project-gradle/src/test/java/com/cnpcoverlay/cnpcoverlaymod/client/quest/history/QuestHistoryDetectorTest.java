package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class QuestHistoryDetectorTest {
    @Test
    void firstObservationCreatesOnlyTheBaseline() {
        var diff = QuestHistoryDetector.diff(Map.of(), Map.of("7", 10L), false);
        assertEquals(Map.of("7", 10L), diff.baseline());
        assertTrue(diff.completions().isEmpty());
    }

    @Test
    void addedOrChangedStampCreatesOneCompletion() {
        var added = QuestHistoryDetector.diff(Map.of("7", 10L), Map.of("7", 10L, "8", 20L), true);
        var changed = QuestHistoryDetector.diff(Map.of("7", 10L), Map.of("7", 11L), true);
        assertEquals(List.of(new QuestHistoryDetector.Completion("8", 20L)), added.completions());
        assertEquals(List.of(new QuestHistoryDetector.Completion("7", 11L)), changed.completions());
    }

    @Test
    void removalOrUnchangedStampCreatesNoCompletion() {
        assertTrue(QuestHistoryDetector.diff(Map.of("7", 10L), Map.of(), true).completions().isEmpty());
        assertTrue(QuestHistoryDetector.diff(Map.of("7", 10L), Map.of("7", 10L), true).completions().isEmpty());
    }

    @Test
    void removalKeepsTheHistoricalBaselineSoAnIdenticalStampDoesNotReappear() {
        var removal = QuestHistoryDetector.diff(Map.of("7", 10L), Map.of(), true);
        var identicalReturn = QuestHistoryDetector.diff(removal.baseline(), Map.of("7", 10L), true);

        assertEquals(Map.of("7", 10L), removal.baseline());
        assertTrue(identicalReturn.completions().isEmpty());
    }
}
