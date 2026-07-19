package com.cnpcoverlay.cnpcoverlaymod.common.quest.history;

import java.util.List;
import java.util.Objects;

/** Événement de remise validée par le serveur. */
public record QuestHistoryEntry(String occurrenceId, String questId, String category, String title,
                                String displayLogText, List<String> objectives,
                                long completedAtEpochMillis, long sourceFinishedStamp, long sequence) {
    public QuestHistoryEntry {
        occurrenceId = Objects.requireNonNullElse(occurrenceId, "");
        questId = Objects.requireNonNullElse(questId, "");
        category = Objects.requireNonNullElse(category, "");
        title = Objects.requireNonNullElse(title, "Quête " + questId);
        displayLogText = Objects.requireNonNullElse(displayLogText, "");
        objectives = List.copyOf(Objects.requireNonNullElse(objectives, List.of()));
    }
}
