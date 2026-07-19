package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import com.cnpcoverlay.cnpcoverlaymod.client.integration.QuestProvider;
import com.cnpcoverlay.cnpcoverlaymod.client.HudDirectionalRenderer;
import com.cnpcoverlay.cnpcoverlaymod.client.integration.customnpcs.CustomNpcsQuestProvider;
import com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap.QuestMapMetadata;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** État client des quêtes, persistance par contexte et production des intentions de navigation. */
public final class QuestTrackerState {
    private static final QuestTrackerState INSTANCE = new QuestTrackerState();
    private final List<QuestEntry> quests = new ArrayList<>();
    private final Map<String, QuestSnapshot> snapshotsById = new HashMap<>();
    private final Set<String> followedQuestIds = ConcurrentHashMap.newKeySet();
    private final QuestProvider provider = new CustomNpcsQuestProvider();
    private final QuestMarkerPlanner markerPlanner = new QuestMarkerPlanner();
    private List<QuestMarkerPlanner.DesiredMarker> desiredMarkers = List.of();
    private String activeQuestId;
    private String loadedContextKey;
    private Player lastKnownPlayer;

    private QuestTrackerState() {}

    public static QuestTrackerState get() { return INSTANCE; }

    public List<QuestEntry> getQuests() { return Collections.unmodifiableList(quests); }

    public List<QuestEntry> getFollowedQuests() { return quests.stream().filter(QuestEntry::isFollowed).toList(); }

    public List<QuestMarkerPlanner.DesiredMarker> getDesiredMarkers() { return desiredMarkers; }

    public Optional<QuestEntry> getActiveQuest() {
        return activeQuestId == null ? Optional.empty() : quests.stream().filter(q -> q.id().equals(activeQuestId)).findFirst();
    }

    public void setFollowed(String questId, boolean followed) {
        Objects.requireNonNull(questId, "questId");
        boolean changed = followed ? followedQuestIds.add(questId) : followedQuestIds.remove(questId);
        quests.stream().filter(quest -> quest.id().equals(questId)).findFirst().ifPresent(quest -> quest.setFollowed(followed));
        if (followed && activeQuestId == null) {
            activeQuestId = questId;
            changed = true;
        } else if (!followed && questId.equals(activeQuestId)) {
            activeQuestId = getFollowedQuests().stream().findFirst().map(QuestEntry::id).orElse(null);
            changed = true;
        }
        if (changed) {
            rebuildMarkers();
            persistIfPlayerKnown();
        }
    }

    public void setActiveQuest(String questId) {
        Objects.requireNonNull(questId, "questId");
        if (!questId.equals(activeQuestId) && followedQuestIds.contains(questId)) {
            activeQuestId = questId;
            persistIfPlayerKnown();
        }
    }

    /** Appelé périodiquement côté client ; aucune réflexion ni I/O n'a lieu pendant le rendu HUD. */
    public void refresh(Player player) {
        if (player == null) {
            return;
        }
        lastKnownPlayer = player;
        String contextKey = QuestPersistenceManager.get().resolveContextKey();
        if (!contextKey.equals(loadedContextKey)) {
            resetForContext(contextKey, player);
        }

        List<QuestSnapshot> snapshots = provider.isAvailable() ? provider.getActiveQuests(player) : List.of();
        quests.clear();
        snapshotsById.clear();
        for (QuestSnapshot snapshot : snapshots) {
            QuestEntry entry = new QuestEntry(snapshot.id(), snapshot.category(), snapshot.title(), snapshot.rawLogText(),
                    snapshot.objectiveSnapshots(), snapshot.progress(), snapshot.completed(), snapshot.completerName());
            entry.setFollowed(followedQuestIds.contains(entry.id()));
            quests.add(entry);
            snapshotsById.put(snapshot.id(), snapshot);
        }
        if (activeQuestId != null && !followedQuestIds.contains(activeQuestId)) {
            activeQuestId = null;
        }
        if (activeQuestId == null) {
            activeQuestId = getFollowedQuests().stream().findFirst().map(QuestEntry::id).orElse(null);
        }
        rebuildMarkers();
    }

    public void clearForDisconnect() {
        quests.clear();
        snapshotsById.clear();
        followedQuestIds.clear();
        desiredMarkers = List.of();
        activeQuestId = null;
        loadedContextKey = null;
        lastKnownPlayer = null;
        QuestMarkerPublisher.clear();
        HudDirectionalRenderer.clearSessionState();
    }

    private void resetForContext(String contextKey, Player player) {
        followedQuestIds.clear();
        activeQuestId = null;
        desiredMarkers = List.of();
        QuestMarkerPublisher.clear();
        Set<String> savedIds = QuestPersistenceManager.get().loadFollowedQuestIds(player);
        followedQuestIds.addAll(savedIds); // même un ensemble vide remplace intégralement la mémoire
        activeQuestId = QuestPersistenceManager.get().loadActiveQuestId(player);
        loadedContextKey = contextKey;
    }

    private void rebuildMarkers() {
        if (lastKnownPlayer == null) {
            return;
        }
        String dimension = lastKnownPlayer.level().dimension().location().toString();
        String contextHash = Integer.toHexString(Objects.requireNonNullElse(loadedContextKey, "unknown").hashCode());
        String playerId = lastKnownPlayer.getUUID().toString();
        List<QuestMarkerPlanner.DesiredMarker> planned = new ArrayList<>();
        for (QuestSnapshot snapshot : snapshotsById.values()) {
            planned.addAll(markerPlanner.plan(snapshot, QuestMapMetadata.parse(snapshot.rawLogText()),
                    followedQuestIds.contains(snapshot.id()), dimension, contextHash, playerId));
        }
        desiredMarkers = List.copyOf(planned);
        QuestMarkerPublisher.publish(desiredMarkers);
    }

    private void persistIfPlayerKnown() {
        if (lastKnownPlayer != null && loadedContextKey != null) {
            QuestPersistenceManager.get().save(lastKnownPlayer, Set.copyOf(followedQuestIds), activeQuestId);
        }
    }
}
