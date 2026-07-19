package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import java.util.List;
import java.util.Objects;

/** Instantané immuable d'une quête, lu exclusivement depuis le miroir client CustomNPCs. */
public record QuestSnapshot(
        String id,
        String category,
        String title,
        String rawLogText,
        List<QuestObjectiveSnapshot> objectiveSnapshots,
        float progress,
        boolean completed,
        String completerName
) {
    public QuestSnapshot {
        id = Objects.requireNonNull(id, "id");
        category = Objects.requireNonNullElse(category, "");
        title = Objects.requireNonNullElse(title, "");
        rawLogText = Objects.requireNonNullElse(rawLogText, "");
        objectiveSnapshots = List.copyOf(Objects.requireNonNullElse(objectiveSnapshots, List.of()));
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        completerName = Objects.requireNonNullElse(completerName, "");
    }

    public List<String> objectives() {
        return objectiveSnapshots.stream().map(QuestObjectiveSnapshot::displayText).toList();
    }
}
