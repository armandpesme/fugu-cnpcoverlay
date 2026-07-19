package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import java.util.List;
import java.util.Objects;

public record QuestHistoryEntry(
        String occurrenceId,
        String questId,
        String category,
        String title,
        String displayLogText,
        List<String> objectives,
        long observedCompletedAtEpochMillis,
        long sourceFinishedStamp,
        long sequence
) {
    public QuestHistoryEntry {
        occurrenceId = Objects.requireNonNull(occurrenceId);
        questId = Objects.requireNonNull(questId);
        category = Objects.requireNonNullElse(category, "");
        title = Objects.requireNonNullElse(title, "Quête " + questId);
        displayLogText = Objects.requireNonNullElse(displayLogText, "");
        objectives = List.copyOf(Objects.requireNonNullElse(objectives, List.of()));
    }
}
