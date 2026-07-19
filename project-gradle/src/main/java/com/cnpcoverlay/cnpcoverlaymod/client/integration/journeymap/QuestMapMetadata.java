package com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Métadonnées de navigation extraites des lignes {@code #} dans la description
 * d'une quête CustomNPCs.
 * <p>
 * Format supporté :
 * <ul>
 *   <li>{@code #!<index>-<x>,<z>,<y>,<radius>} — objectif incomplet</li>
 *   <li>{@code #?-<x>,<z>,<y>} — quête à rendre (turn-in)</li>
 * </ul>
 * Plusieurs entrées peuvent être collées sans séparateur.
 * Les coordonnées sont au format X, Z, Y (ordre projet).
 */
public final class QuestMapMetadata {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<ObjectiveMarker> objectiveMarkers;
    private final Optional<TurnInMarker> turnInMarker;

    private QuestMapMetadata(List<ObjectiveMarker> objectiveMarkers, TurnInMarker turnInMarker) {
        this.objectiveMarkers = Collections.unmodifiableList(objectiveMarkers);
        this.turnInMarker = Optional.ofNullable(turnInMarker);
    }

    // ---------------------------------------------------------------
    // API publique
    // ---------------------------------------------------------------

    public List<ObjectiveMarker> objectiveMarkers() {
        return objectiveMarkers;
    }

    public Optional<TurnInMarker> turnInMarker() {
        return turnInMarker;
    }

    public boolean isEmpty() {
        return objectiveMarkers.isEmpty() && turnInMarker.isEmpty();
    }

    // ---------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------

    /**
     * Parse le {@code logText} brut d'une quête et extrait les métadonnées {@code #!} et {@code #?}.
     *
     * @param logText le texte brut de la description (contenant les lignes {@code #}).
     * @return {@code QuestMapMetadata} — jamais {@code null}, peut être vide.
     */
    public static QuestMapMetadata parse(String logText) {
        if (logText == null || logText.isBlank()) {
            return empty();
        }

        Map<Integer, ObjectiveMarker> objectives = new LinkedHashMap<>();
        TurnInMarker turnIn = null;

        String[] lines = logText.split("\\n", -1);
        for (String rawLine : lines) {
            String line = rawLine.stripLeading();
            if (!line.startsWith("#")) {
                continue;
            }

            TurnInMarker[] turnInHolder = (turnIn == null) ? new TurnInMarker[1] : null;
            parseCompoundLine(line, objectives, turnInHolder);
            if (turnInHolder != null && turnInHolder[0] != null) {
                turnIn = turnInHolder[0];
            }
        }

        return new QuestMapMetadata(new ArrayList<>(objectives.values()), turnIn);
    }

    /**
     * Parcourt une ligne pouvant contenir plusieurs {@code #!...} ou {@code #?...}
     * collés sans séparateur.
     */
    private static void parseCompoundLine(String line, Map<Integer, ObjectiveMarker> objectives, TurnInMarker[] turnInHolder) {
        int pos = 0;
        while (pos < line.length()) {
            int markerStart = line.indexOf("#!", pos);
            int turnInStart = line.indexOf("#?", pos);

            if (markerStart == -1 && turnInStart == -1) {
                break;
            }

            if (turnInStart != -1 && (markerStart == -1 || turnInStart < markerStart)) {
                // #? turn-in : parser jusqu'au prochain marqueur ou fin
                int end = nextMarkerBound(line, turnInStart + 2);
                String content = line.substring(turnInStart + 2, end);
                TurnInMarker parsed = parseTurnInMarker(content);
                if (parsed != null && turnInHolder != null) {
                    turnInHolder[0] = parsed;
                }
                pos = end;
            } else {
                // #! objectif : parser jusqu'au prochain marqueur ou fin
                int end = nextMarkerBound(line, markerStart + 2);
                String content = line.substring(markerStart + 2, end);
                ObjectiveMarker parsed = parseObjectiveMarker(content);
                if (parsed != null) {
                    // La dernière déclaration valide d'un index est la source de vérité.
                    objectives.remove(parsed.objectiveIndex());
                    objectives.put(parsed.objectiveIndex(), parsed);
                }
                pos = end;
            }
        }
    }

    /**
     * Retourne l'index du prochain {@code #!} ou {@code #?} après {@code from},
     * ou la fin de la chaîne.
     */
    private static int nextMarkerBound(String line, int from) {
        int nextExclaim = line.indexOf("#!", from);
        int nextQuestion = line.indexOf("#?", from);
        int end = line.length();
        if (nextExclaim != -1 && nextExclaim < end) end = nextExclaim;
        if (nextQuestion != -1 && nextQuestion < end) end = nextQuestion;
        return end;
    }

    /**
     * Parse {@code <index>-<x>,<z>,<y>,<radius>}
     */
    private static ObjectiveMarker parseObjectiveMarker(String content) {
        try {
            int dashIndex = content.indexOf('-');
            if (dashIndex < 0) {
                LOGGER.debug("Format #! invalide (pas de tiret) : {}", content);
                return null;
            }

            int objectiveIndex = Integer.parseInt(content.substring(0, dashIndex));

            String coordsStr = content.substring(dashIndex + 1);
            String[] parts = coordsStr.split(",");
            if (parts.length < 4) {
                LOGGER.debug("Format #! invalide (4 parties attendues) : {}", content);
                return null;
            }

            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int radius = Integer.parseInt(parts[3].trim());

            if (objectiveIndex < 1 || radius < 0) {
                LOGGER.debug("Format #! invalide (index ou rayon) : {}", content);
                return null;
            }
            return new ObjectiveMarker(objectiveIndex, x, z, y, radius);
        } catch (NumberFormatException e) {
            LOGGER.debug("Format #! invalide (nombre) : {} — {}", content, e.getMessage());
            return null;
        }
    }

    /**
     * Parse {@code <x>,<z>,<y>}
     */
    private static TurnInMarker parseTurnInMarker(String content) {
        try {
            String normalized = content.trim();

            // Certaines versions de CustomNPCs sérialisent le séparateur du
            // marqueur en plus du signe de la coordonnée : #?--1235,...
            // (et parfois #?<index>--1235,...). Retirer uniquement ce
            // séparateur supplémentaire, sans toucher aux autres signes.
            if (normalized.matches("^\\d+\\s*-\\s*[-+]?\\d+\\s*,.*")) {
                int separator = normalized.indexOf('-');
                normalized = normalized.substring(separator + 1).trim();
            }
            if (normalized.startsWith("--")) {
                normalized = normalized.substring(1).trim();
            }

            String[] parts = normalized.split(",");
            if (parts.length < 3) {
                LOGGER.debug("Format #? invalide (3 parties attendues) : {}", content);
                return null;
            }
            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            return new TurnInMarker(x, z, y);
        } catch (NumberFormatException e) {
            LOGGER.debug("Format #? invalide (nombre) : {} — {}", content, e.getMessage());
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Factories
    // ---------------------------------------------------------------

    public static QuestMapMetadata empty() {
        return new QuestMapMetadata(List.of(), null);
    }

    // ---------------------------------------------------------------
    // Records de données
    // ---------------------------------------------------------------

    /**
     * Marqueur d'objectif incomplet ({@code #!}).
     *
     * @param objectiveIndex index de l'objectif (1-based dans le format CustomNPCs).
     * @param x              coordonnée X (monde Minecraft).
     * @param z              coordonnée Z (monde Minecraft).
     * @param y              coordonnée Y (monde Minecraft).
     * @param radius         rayon en blocs autour du point.
     */
    public record ObjectiveMarker(int objectiveIndex, int x, int z, int y, int radius) {
    }

    /**
     * Marqueur de remise de quête ({@code #?}).
     *
     * @param x coordonnée X (monde Minecraft).
     * @param z coordonnée Z (monde Minecraft).
     * @param y coordonnée Y (monde Minecraft).
     */
    public record TurnInMarker(int x, int z, int y) {
    }
}
