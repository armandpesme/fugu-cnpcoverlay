package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import java.util.Objects;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;

public final class QuestEntry {
    private final String id;
    private final String category;
    private final String title;
    private final String logText;
    private final List<String> objectives;
    private final List<QuestObjectiveSnapshot> objectiveSnapshots;
    private final String objective;
    /** 0..1 */
    private final float progress;
    private final String completerName;

    private boolean followed;

    public QuestEntry(String id, String category, String title, String logText, List<String> objectives, float progress) {
        this(id, category, title, logText, toSnapshots(objectives), progress, false, "");
    }

    public QuestEntry(String id, String category, String title, String logText, List<QuestObjectiveSnapshot> objectiveSnapshots,
                      float progress, boolean completed, String completerName) {
        this.id = Objects.requireNonNull(id);
        this.category = Objects.requireNonNullElse(category, "");
        this.title = Objects.requireNonNull(title);
        this.logText = Objects.requireNonNullElse(logText, "");
        this.objectiveSnapshots = objectiveSnapshots == null ? List.of() : List.copyOf(objectiveSnapshots);
        this.objectives = this.objectiveSnapshots.stream().map(QuestObjectiveSnapshot::displayText).toList();
        this.objective = this.objectives.isEmpty() ? "" : String.valueOf(this.objectives.get(0));
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        this.completerName = Objects.requireNonNullElse(completerName, "").trim();
    }

    public String id() {
        return id;
    }

    public String category() {
        return category;
    }

    public String title() {
        return title;
    }

    public String logText() {
        return logText;
    }

    /**
     * Returns the description intended for display, without CustomNPCs metadata lines.
     */
    public String displayLogText() {
        return String.join("\n", Arrays.stream(logText.split("\\n", -1))
                .filter(line -> !line.stripLeading().startsWith("#"))
                .toList());
    }

    public List<String> objectives() {
        return objectives;
    }

    public List<QuestObjectiveSnapshot> objectiveSnapshots() {
        return objectiveSnapshots;
    }

    public String objective() {
        return objective;
    }

    public float progress() {
        return progress;
    }

    /**
     * Returns the turn-in instruction once the tracked quest has reached 100%.
     */
    public Optional<String> turnInInstruction() {
        if (progress < 0.999f || completerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of("Aller voir " + completerName);
    }

    public boolean isFollowed() {
        return followed;
    }

    public void setFollowed(boolean followed) {
        this.followed = followed;
    }

    private static List<QuestObjectiveSnapshot> toSnapshots(List<String> objectives) {
        if (objectives == null) {
            return List.of();
        }
        return java.util.stream.IntStream.range(0, objectives.size())
                .mapToObj(index -> new QuestObjectiveSnapshot(index + 1, objectives.get(index), 0, 0, false))
                .toList();
    }
}
