package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QuestHistoryDetector {
    public record Completion(String questId, long sourceStamp) {}

    public record Diff(Map<String, Long> baseline, List<Completion> completions) {}

    public static Diff diff(Map<String, Long> previous, Map<String, Long> current, boolean initialized) {
        Map<String, Long> mergedBaseline = new HashMap<>(previous);
        mergedBaseline.putAll(current);
        Map<String, Long> baseline = Map.copyOf(mergedBaseline);
        if (!initialized) {
            return new Diff(baseline, List.of());
        }
        List<Completion> completions = current.entrySet().stream()
                .filter(entry -> !Objects.equals(previous.get(entry.getKey()), entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new Completion(entry.getKey(), entry.getValue()))
                .toList();
        return new Diff(baseline, completions);
    }
}
