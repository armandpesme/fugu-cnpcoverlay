package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap.QuestMapMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Transforme les métadonnées de quête en intentions de rendu sans dépendre de JourneyMap ou de Minecraft. */
public final class QuestMarkerPlanner {
    public static final int OBJECTIVE_COLOR = 0x9B30FF;
    public static final int TURN_IN_COLOR = 0xFF8C00;

    public List<DesiredMarker> plan(QuestSnapshot quest, QuestMapMetadata metadata, boolean followed,
                                    String dimensionId, String contextHash, String playerId) {
        Objects.requireNonNull(quest, "quest");
        Objects.requireNonNull(metadata, "metadata");
        if (!followed || metadata.isEmpty()) {
            return List.of();
        }

        String prefix = "cnpcoverlay:" + safe(contextHash) + ':' + safe(playerId) + ':' + safe(quest.id());
        String dimension = Objects.requireNonNullElse(dimensionId, "minecraft:overworld");
        List<DesiredMarker> result = new ArrayList<>();
        if (quest.completed() || quest.progress() >= 1.0f) {
            metadata.turnInMarker().ifPresent(marker -> result.add(new DesiredMarker(
                    prefix + ":turnin:marker", MarkerType.TURN_IN, 0, marker.x(), marker.y(), marker.z(), 0,
                    TURN_IN_COLOR, dimension, quest.title(), tooltip(quest, "À rendre", marker.x(), marker.y(), marker.z(), 0))));
            return List.copyOf(result);
        }

        for (QuestMapMetadata.ObjectiveMarker marker : metadata.objectiveMarkers()) {
            QuestObjectiveSnapshot objective = quest.objectiveSnapshots().stream()
                    .filter(snapshot -> snapshot.index() == marker.objectiveIndex())
                    .findFirst().orElse(null);
            if (objective == null || objective.completed()) {
                continue;
            }
            String label = "!" + marker.objectiveIndex();
            result.add(new DesiredMarker(prefix + ":objective:" + marker.objectiveIndex() + ":marker",
                    MarkerType.OBJECTIVE, marker.objectiveIndex(), marker.x(), marker.y(), marker.z(), marker.radius(),
                    OBJECTIVE_COLOR, dimension, quest.title(), tooltip(quest, objective.displayText(), marker.x(), marker.y(), marker.z(), marker.radius())));
            if (marker.radius() > 0) {
                result.add(new DesiredMarker(prefix + ":objective:" + marker.objectiveIndex() + ":radius",
                        MarkerType.RADIUS, marker.objectiveIndex(), marker.x(), marker.y(), marker.z(), marker.radius(),
                        OBJECTIVE_COLOR, dimension, quest.title(), label));
            }
        }
        return List.copyOf(result);
    }

    private static String tooltip(QuestSnapshot quest, String status, int x, int y, int z, int radius) {
        String suffix = radius > 0 ? ", rayon " + radius : "";
        return quest.title() + "\n" + status + "\nX=" + x + " Y=" + y + " Z=" + z + suffix;
    }

    private static String safe(String value) {
        return Objects.requireNonNullElse(value, "unknown").replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public enum MarkerType { OBJECTIVE, TURN_IN, RADIUS }

    public record DesiredMarker(String id, MarkerType type, int objectiveIndex, int x, int y, int z, int radius,
                                int color, String dimensionId, String title, String tooltip) {
        public DesiredMarker {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(dimensionId, "dimensionId");
            title = Objects.requireNonNullElse(title, "");
            tooltip = Objects.requireNonNullElse(tooltip, "");
        }
    }
}
