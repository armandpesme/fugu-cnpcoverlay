package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import com.cnpcoverlay.cnpcoverlaymod.client.integration.QuestProvider.FinishedQuestStamps;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestPersistenceManager;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestSnapshot;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** État client de l'historique, alimenté hors rendu depuis le miroir CustomNPCs. */
public final class QuestHistoryState {
    private static final int MISSING_CONFIRMATION_COUNT = 3;

    private final Path storePath;
    private final QuestHistoryWriteQueue writeQueue;
    private final Map<String, QuestSnapshot> snapshotsById = new HashMap<>();
    private final Map<String, Integer> forcedMissingCounts = new HashMap<>();
    private List<QuestHistoryEntry> entries = List.of();
    private Map<String, Long> lastFinishedStamps = Map.of();
    private String loadedContextKey;
    private String loadedPlayerKey;
    private String loadingContextKey;
    private String loadingPlayerKey;
    private CompletableFuture<QuestHistoryStore.PlayerHistory> pendingLoad;
    private final List<PendingObservation> observationsDuringLoad = new ArrayList<>();
    private Object lastIdentityToken;
    private long nextSequence;
    private boolean baselineInitialized;

    private QuestHistoryState() {
        this.storePath = null;
        this.writeQueue = QuestHistoryWriteQueue.get();
    }

    QuestHistoryState(Path storePath) {
        this(storePath, QuestHistoryWriteQueue.direct());
    }

    QuestHistoryState(Path storePath, QuestHistoryWriteQueue writeQueue) {
        this.storePath = Objects.requireNonNull(storePath, "storePath");
        this.writeQueue = Objects.requireNonNull(writeQueue, "writeQueue");
    }

    public static QuestHistoryState get() {
        return Holder.INSTANCE;
    }

    public List<QuestHistoryEntry> entries() {
        return entries;
    }

    public void observe(
            Player player,
            List<QuestSnapshot> snapshots,
            FinishedQuestStamps finishedQuestStamps,
            boolean force
    ) {
        if (player == null) {
            return;
        }
        String contextKey = QuestPersistenceManager.get().resolveContextKey();
        observe(
                contextKey,
                player.getUUID().toString(),
                snapshots,
                finishedQuestStamps,
                force,
                System.currentTimeMillis()
        );
    }

    public void clearForDisconnect() {
        writeQueue.flush();
        snapshotsById.clear();
        entries = List.of();
        lastFinishedStamps = Map.of();
        loadedContextKey = null;
        loadedPlayerKey = null;
        loadingContextKey = null;
        loadingPlayerKey = null;
        pendingLoad = null;
        observationsDuringLoad.clear();
        forcedMissingCounts.clear();
        lastIdentityToken = null;
        nextSequence = 0L;
        baselineInitialized = false;
    }

    void observe(
            String contextKey,
            String playerKey,
            List<QuestSnapshot> snapshots,
            FinishedQuestStamps finishedQuestStamps,
            boolean force,
            long observedAtEpochMillis
    ) {
        Objects.requireNonNull(contextKey, "contextKey");
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(snapshots, "snapshots");
        Objects.requireNonNull(finishedQuestStamps, "finishedQuestStamps");
        ensureLoadStarted(contextKey, playerKey);
        for (QuestSnapshot snapshot : snapshots) {
            snapshotsById.put(snapshot.id(), snapshot);
        }
        if (pendingLoad != null) {
            captureDuringLoad(finishedQuestStamps, force, observedAtEpochMillis);
            if (!pendingLoad.isDone()) {
                return;
            }
            applyCompletedLoad(contextKey, playerKey);
            return;
        }
        processObservation(
                contextKey,
                playerKey,
                finishedQuestStamps,
                force,
                observedAtEpochMillis
        );
    }

    private void processObservation(
            String contextKey,
            String playerKey,
            FinishedQuestStamps finishedQuestStamps,
            boolean force,
            long observedAtEpochMillis
    ) {
        if (!finishedQuestStamps.available()) {
            return;
        }
        if (!force && finishedQuestStamps.identityToken() == lastIdentityToken) {
            return;
        }
        lastIdentityToken = finishedQuestStamps.identityToken();
        boolean baselineInitializedNow = !baselineInitialized;
        Map<String, Long> previousForDiff = baselineAfterConfirmedRemovals(
                finishedQuestStamps.stamps(),
                force
        );

        QuestHistoryDetector.Diff diff = QuestHistoryDetector.diff(
                previousForDiff,
                finishedQuestStamps.stamps(),
                baselineInitialized
        );
        boolean baselineChanged = !lastFinishedStamps.equals(diff.baseline());
        lastFinishedStamps = diff.baseline();
        baselineInitialized = true;
        if (baselineInitializedNow && nextSequence <= 0L) {
            nextSequence = 1L;
        }

        boolean entriesChanged = !diff.completions().isEmpty();
        if (entriesChanged) {
            List<QuestHistoryEntry> updated = new ArrayList<>(entries);
            for (QuestHistoryDetector.Completion completion : diff.completions()) {
                long sequence = nextSequence <= 0L ? 1L : nextSequence;
                nextSequence = sequence + 1L;
                updated.add(entry(contextKey, playerKey, completion, observedAtEpochMillis, sequence));
            }
            updated.sort(Comparator.comparingLong(QuestHistoryEntry::sequence).reversed());
            entries = List.copyOf(updated);
        }

        if (baselineInitializedNow || baselineChanged || entriesChanged) {
            save(
                    contextKey,
                    playerKey,
                    new QuestHistoryStore.PlayerHistory(nextSequence, lastFinishedStamps, entries)
            );
        }
    }

    private void ensureLoadStarted(String contextKey, String playerKey) {
        if (contextKey.equals(loadedContextKey) && playerKey.equals(loadedPlayerKey)) {
            return;
        }
        if (contextKey.equals(loadingContextKey) && playerKey.equals(loadingPlayerKey)) {
            return;
        }
        resetForLoad();
        Path filePath = resolvedStorePath();
        loadingContextKey = contextKey;
        loadingPlayerKey = playerKey;
        pendingLoad = writeQueue.loadAsync(
                filePath,
                new QuestHistoryWriteQueue.Subject(contextKey, playerKey)
        );
    }

    private void captureDuringLoad(
            FinishedQuestStamps finishedQuestStamps,
            boolean force,
            long observedAtEpochMillis
    ) {
        if (finishedQuestStamps.available()) {
            observationsDuringLoad.add(
                    new PendingObservation(finishedQuestStamps, force, observedAtEpochMillis)
            );
        }
    }

    private void applyCompletedLoad(String contextKey, String playerKey) {
        QuestHistoryStore.PlayerHistory history;
        try {
            history = pendingLoad.join();
        } catch (CompletionException exception) {
            history = QuestHistoryStore.PlayerHistory.empty();
        }
        loadedContextKey = contextKey;
        loadedPlayerKey = playerKey;
        entries = history.entries().stream()
                .sorted(Comparator.comparingLong(QuestHistoryEntry::sequence).reversed())
                .toList();
        lastFinishedStamps = history.lastFinishedStamps();
        nextSequence = history.nextSequence();
        baselineInitialized = nextSequence > 0L || !lastFinishedStamps.isEmpty() || !entries.isEmpty();
        forcedMissingCounts.clear();
        lastIdentityToken = null;
        List<PendingObservation> captured = List.copyOf(observationsDuringLoad);
        observationsDuringLoad.clear();
        loadingContextKey = null;
        loadingPlayerKey = null;
        pendingLoad = null;
        for (PendingObservation observation : captured) {
            processObservation(
                    contextKey,
                    playerKey,
                    observation.finishedQuestStamps(),
                    observation.force(),
                    observation.observedAtEpochMillis()
            );
        }
    }

    private void resetForLoad() {
        snapshotsById.clear();
        entries = List.of();
        lastFinishedStamps = Map.of();
        loadedContextKey = null;
        loadedPlayerKey = null;
        lastIdentityToken = null;
        nextSequence = 0L;
        baselineInitialized = false;
        observationsDuringLoad.clear();
        forcedMissingCounts.clear();
    }

    private Map<String, Long> baselineAfterConfirmedRemovals(
            Map<String, Long> current,
            boolean force
    ) {
        forcedMissingCounts.keySet().removeIf(current::containsKey);
        if (!force || lastFinishedStamps.isEmpty()) {
            return lastFinishedStamps;
        }

        Map<String, Long> baseline = null;
        for (String questId : lastFinishedStamps.keySet()) {
            if (current.containsKey(questId)) {
                continue;
            }
            int missingCount = forcedMissingCounts.getOrDefault(questId, 0) + 1;
            if (missingCount < MISSING_CONFIRMATION_COUNT) {
                forcedMissingCounts.put(questId, missingCount);
                continue;
            }
            forcedMissingCounts.remove(questId);
            if (baseline == null) {
                baseline = new HashMap<>(lastFinishedStamps);
            }
            baseline.remove(questId);
        }
        return baseline == null ? lastFinishedStamps : Map.copyOf(baseline);
    }

    private void save(
            String contextKey,
            String playerKey,
            QuestHistoryStore.PlayerHistory history
    ) {
        writeQueue.enqueue(resolvedStorePath(), contextKey, playerKey, history);
    }

    private Path resolvedStorePath() {
        if (storePath != null) {
            return storePath;
        }
        return QuestHistoryStore.resolveFilePath(net.minecraft.client.Minecraft.getInstance());
    }

    private QuestHistoryEntry entry(
            String contextKey,
            String playerKey,
            QuestHistoryDetector.Completion completion,
            long observedAtEpochMillis,
            long sequence
    ) {
        QuestSnapshot snapshot = snapshotsById.get(completion.questId());
        String category = snapshot == null ? "" : snapshot.category();
        String title = snapshot == null ? "Quête " + completion.questId() : snapshot.title();
        String displayLogText = snapshot == null ? "" : displayLogText(snapshot.rawLogText());
        List<String> objectives = snapshot == null ? List.of() : snapshot.objectives();
        return new QuestHistoryEntry(
                contextKey + ":" + playerKey + ":" + sequence,
                completion.questId(),
                category,
                title,
                displayLogText,
                objectives,
                observedAtEpochMillis,
                completion.sourceStamp(),
                sequence
        );
    }

    private static String displayLogText(String rawLogText) {
        return String.join("\n", Arrays.stream(rawLogText.split("\\n", -1))
                .filter(line -> !line.stripLeading().startsWith("#"))
                .toList());
    }

    private record PendingObservation(
            FinishedQuestStamps finishedQuestStamps,
            boolean force,
            long observedAtEpochMillis
    ) {
    }

    private static final class Holder {
        private static final QuestHistoryState INSTANCE = new QuestHistoryState();
    }
}
