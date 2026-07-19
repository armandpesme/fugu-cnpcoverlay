package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import java.util.Objects;

/** État client synchronisé d'un objectif CustomNPCs, indexé à partir de 1. */
public record QuestObjectiveSnapshot(int index, String text, int current, int maximum, boolean completed) {
    public QuestObjectiveSnapshot {
        if (index < 1) {
            throw new IllegalArgumentException("L'index d'objectif doit être positif");
        }
        text = Objects.requireNonNullElse(text, "");
        current = Math.max(0, current);
        maximum = Math.max(0, maximum);
    }

    public String displayText() {
        if (maximum > 0 && !text.matches(".*\\d+\\s*/\\s*\\d+.*")) {
            return text + ": " + current + "/" + maximum;
        }
        return text;
    }
}
