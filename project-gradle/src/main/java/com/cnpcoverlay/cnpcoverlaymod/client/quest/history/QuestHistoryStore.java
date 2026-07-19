package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestPersistenceManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.MalformedJsonException;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class QuestHistoryStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Map<String, PlayerHistory>>>() {
    }.getType();
    private static final BackupStrategy DEFAULT_BACKUP = (source, target) ->
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);

    private QuestHistoryStore() {
    }

    public static PlayerHistory load(Player player) {
        Objects.requireNonNull(player, "player");
        Minecraft minecraft = Minecraft.getInstance();
        Path filePath = resolveFilePath(minecraft);
        try {
            QuestHistoryWriteQueue.get().awaitPendingWrites();
        } catch (RuntimeException ignored) {
            // Le dernier état durable reste chargeable; un nouvel enqueue retentera.
        }
        return load(
                filePath,
                QuestPersistenceManager.get().resolveContextKey(),
                player.getUUID().toString()
        );
    }

    public static void save(Player player, PlayerHistory history) {
        Objects.requireNonNull(player, "player");
        Minecraft minecraft = Minecraft.getInstance();
        QuestHistoryWriteQueue.get().enqueue(
                resolveFilePath(minecraft),
                QuestPersistenceManager.get().resolveContextKey(),
                player.getUUID().toString(),
                history
        );
    }

    static Path resolveFilePath(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        return minecraft.gameDirectory.toPath().resolve("cnpcoverlay/quest_history.json");
    }

    static PlayerHistory load(Path filePath, String contextKey, String playerKey) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(contextKey, "contextKey");
        Objects.requireNonNull(playerKey, "playerKey");
        if (!Files.exists(filePath)) {
            return PlayerHistory.empty();
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Map<String, Map<String, PlayerHistory>> root = GSON.fromJson(reader, DATA_TYPE);
            if (root == null) {
                return PlayerHistory.empty();
            }
            Map<String, PlayerHistory> context = root.get(contextKey);
            if (context == null) {
                return PlayerHistory.empty();
            }
            return Objects.requireNonNullElse(context.get(playerKey), PlayerHistory.empty());
        } catch (IOException exception) {
            logReadFailure(filePath, exception);
            return PlayerHistory.empty();
        } catch (JsonParseException exception) {
            IOException ioFailure = findIOException(exception);
            if (ioFailure != null) {
                logReadFailure(filePath, ioFailure);
                return PlayerHistory.empty();
            }
            backupBrokenFile(filePath, exception);
            return PlayerHistory.empty();
        } catch (RuntimeException exception) {
            logReadFailure(filePath, exception);
            return PlayerHistory.empty();
        }
    }

    static void save(Path filePath, String contextKey, String playerKey, PlayerHistory history) {
        save(filePath, contextKey, playerKey, history, Files::newBufferedWriter, DEFAULT_BACKUP);
    }

    static void save(
            Path filePath,
            String contextKey,
            String playerKey,
            PlayerHistory history,
            WriterFactory writerFactory
    ) {
        save(filePath, contextKey, playerKey, history, writerFactory, DEFAULT_BACKUP);
    }

    static void save(
            Path filePath,
            String contextKey,
            String playerKey,
            PlayerHistory history,
            WriterFactory writerFactory,
            BackupStrategy backupStrategy
    ) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(contextKey, "contextKey");
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(writerFactory, "writerFactory");
        Objects.requireNonNull(backupStrategy, "backupStrategy");

        try {
            saveBatch(
                    filePath,
                    Map.of(new QuestHistoryWriteQueue.Subject(contextKey, playerKey), history),
                    writerFactory,
                    backupStrategy
            );
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Impossible d'écrire l'historique des quêtes : {}", exception.getMessage());
        }
    }

    static void saveBatch(
            Path filePath,
            Map<QuestHistoryWriteQueue.Subject, PlayerHistory> histories
    ) throws IOException {
        saveBatch(filePath, histories, Files::newBufferedWriter, DEFAULT_BACKUP);
    }

    private static void saveBatch(
            Path filePath,
            Map<QuestHistoryWriteQueue.Subject, PlayerHistory> histories,
            WriterFactory writerFactory,
            BackupStrategy backupStrategy
    ) throws IOException {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(histories, "histories");
        Objects.requireNonNull(writerFactory, "writerFactory");
        Objects.requireNonNull(backupStrategy, "backupStrategy");

        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Map<String, Map<String, PlayerHistory>> root = readForSave(filePath, backupStrategy);
        for (Map.Entry<QuestHistoryWriteQueue.Subject, PlayerHistory> entry : histories.entrySet()) {
            QuestHistoryWriteQueue.Subject subject =
                    Objects.requireNonNull(entry.getKey(), "subject");
            PlayerHistory history = Objects.requireNonNull(entry.getValue(), "history");
            Map<String, PlayerHistory> context = new HashMap<>(
                    root.getOrDefault(subject.contextKey(), Map.of())
            );
            context.put(subject.playerKey(), history);
            root.put(subject.contextKey(), context);
        }
        Path temporaryPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        try {
            try (Writer writer = writerFactory.open(temporaryPath)) {
                GSON.toJson(root, DATA_TYPE, writer);
            }
            moveAtomically(temporaryPath, filePath);
        } catch (IOException | RuntimeException exception) {
            cleanupTemporaryFile(temporaryPath);
            throw exception;
        }
    }

    private static Map<String, Map<String, PlayerHistory>> readForSave(
            Path filePath,
            BackupStrategy backupStrategy
    ) throws IOException {
        if (!Files.exists(filePath)) {
            return new HashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Map<String, Map<String, PlayerHistory>> root = GSON.fromJson(reader, DATA_TYPE);
            return root == null ? new HashMap<>() : new HashMap<>(root);
        } catch (JsonParseException exception) {
            IOException ioFailure = findIOException(exception);
            if (ioFailure != null) {
                throw ioFailure;
            }
            backupBrokenFile(filePath, exception, backupStrategy);
            return new HashMap<>();
        }
    }

    private static void moveAtomically(Path temporaryPath, Path filePath) throws IOException {
        try {
            Files.move(
                    temporaryPath,
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void cleanupTemporaryFile(Path temporaryPath) {
        try {
            Files.deleteIfExists(temporaryPath);
        } catch (IOException | RuntimeException cleanupFailure) {
            LOGGER.warn(
                    "Impossible de nettoyer le fichier temporaire {} : {}",
                    temporaryPath.getFileName(),
                    cleanupFailure.getMessage()
            );
        }
    }

    private static IOException findIOException(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof IOException ioException
                    && !(ioException instanceof EOFException)
                    && !(ioException instanceof MalformedJsonException)) {
                return ioException;
            }
            Throwable next = cause.getCause();
            if (next == cause) {
                break;
            }
            cause = next;
        }
        return null;
    }

    private static void logReadFailure(Path filePath, Exception exception) {
        LOGGER.warn(
                "Impossible de lire le fichier d'historique {} : {}",
                filePath.getFileName(),
                exception.getMessage()
        );
    }

    private static void backupBrokenFile(Path filePath, Exception exception) {
        try {
            backupBrokenFile(filePath, exception, DEFAULT_BACKUP);
        } catch (IOException ignored) {
            // L'échec est déjà journalisé; un load ne doit jamais propager l'I/O.
        }
    }

    private static void backupBrokenFile(
            Path filePath,
            Exception exception,
            BackupStrategy backupStrategy
    ) throws IOException {
        Path backup = filePath.resolveSibling(
                filePath.getFileName() + ".broken-" + System.currentTimeMillis()
        );
        try {
            backupStrategy.move(filePath, backup);
            LOGGER.warn(
                    "Fichier d'historique corrompu déplacé vers {} : {}",
                    backup.getFileName(),
                    exception.getMessage()
            );
        } catch (IOException moveFailure) {
            LOGGER.warn(
                    "Fichier d'historique corrompu (copie impossible) : {}",
                    moveFailure.getMessage()
            );
            throw moveFailure;
        }
    }

    public record PlayerHistory(
            long nextSequence,
            Map<String, Long> lastFinishedStamps,
            List<QuestHistoryEntry> entries
    ) {
        public PlayerHistory {
            lastFinishedStamps = Map.copyOf(
                    Objects.requireNonNullElse(lastFinishedStamps, Map.of())
            );
            entries = List.copyOf(Objects.requireNonNullElse(entries, List.of()));
        }

        public static PlayerHistory empty() {
            return new PlayerHistory(0L, Map.of(), List.of());
        }
    }

    @FunctionalInterface
    interface WriterFactory {
        Writer open(Path path) throws IOException;
    }

    @FunctionalInterface
    interface BackupStrategy {
        void move(Path source, Path target) throws IOException;
    }
}
