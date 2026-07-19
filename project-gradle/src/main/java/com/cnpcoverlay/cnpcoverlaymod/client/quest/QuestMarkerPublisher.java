package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import java.util.List;
import java.util.function.Consumer;

/** Pont sans type JourneyMap : le plugin optionnel installe le consommateur à son initialisation. */
public final class QuestMarkerPublisher {
    private static List<QuestMarkerPlanner.DesiredMarker> latest = List.of();
    private static Consumer<List<QuestMarkerPlanner.DesiredMarker>> consumer;

    private QuestMarkerPublisher() {}

    public static void install(Consumer<List<QuestMarkerPlanner.DesiredMarker>> newConsumer) {
        consumer = newConsumer;
        consumer.accept(latest);
    }

    public static void publish(List<QuestMarkerPlanner.DesiredMarker> markers) {
        latest = List.copyOf(markers);
        if (consumer != null) {
            consumer.accept(latest);
        }
    }

    public static void clear() {
        publish(List.of());
    }
}
