package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Persiste localement les quêtes suivies par joueur et par serveur dans un fichier JSON.
 * <p>
 * Format du fichier {@code cnpcoverlay/tracked_quests.json} dans le dossier de jeu&nbsp;:
 * <pre>{@code
 * {
 *   "serverKey": {
 *     "playerUuid": {
 *       "followedQuestIds": ["1", "2"],
 *       "seenQuestIds": ["1", "2", "3"],
 *       "activeQuestId": "1"
 *     }
 *   }
 * }
 * }</pre>
 */
public final class QuestPersistenceManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final QuestPersistenceManager INSTANCE = new QuestPersistenceManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Type DATA_TYPE = new TypeToken<Map<String, Map<String, PlayerData>>>() {
    }.getType();

    private static final String FILENAME = "cnpcoverlay/tracked_quests.json";

    private QuestPersistenceManager() {
    }

    public static QuestPersistenceManager get() {
        return INSTANCE;
    }

    // -----------------------------------------------------------------------
    // API publique
    // -----------------------------------------------------------------------

    /**
     * Charge l'ensemble des IDs de quêtes suivies pour le joueur courant.
     *
     * @param player le joueur (non-null) servant à dériver la clé.
     * @return ensemble immutable, jamais {@code null}.
     */
    public Set<String> loadFollowedQuestIds(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerData data = loadPlayerData(player);
        return data == null ? Collections.emptySet() : Collections.unmodifiableSet(data.followedQuestIds);
    }

    /**
     * Charge l'ID de la quête active pour le joueur courant.
     *
     * @param player le joueur (non-null).
     * @return l'ID actif ou {@code null} si absent.
     */
    public String loadActiveQuestId(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerData data = loadPlayerData(player);
        return data == null ? null : data.activeQuestId;
    }

    /** Charge les quêtes déjà rencontrées afin de ne pas réactiver un décochage manuel. */
    public Set<String> loadSeenQuestIds(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerData data = loadPlayerData(player);
        return data == null || data.seenQuestIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(data.seenQuestIds);
    }

    /** Indique si la persistance contient une baseline de quêtes déjà rencontrées. */
    public boolean hasSeenQuestIds(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerData data = loadPlayerData(player);
        return data != null && data.seenQuestIds != null;
    }

    /**
     * Sauvegarde l'état courant des quêtes suivies et de la quête active.
     * Écriture atomique&nbsp;: fichier temporaire puis renommage.
     *
     * @param player            le joueur courant (non-null).
     * @param followedQuestIds  ensemble des IDs suivis.
     * @param activeQuestId     ID de la quête active, peut être {@code null}.
     */
    public void save(Player player, Set<String> followedQuestIds, Set<String> seenQuestIds, String activeQuestId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(followedQuestIds, "followedQuestIds");
        Objects.requireNonNull(seenQuestIds, "seenQuestIds");

        Path filePath = resolveFilePath();
        if (filePath == null) {
            return;
        }
        saveToPath(filePath, resolveContextKey(), player.getUUID().toString(), followedQuestIds, seenQuestIds, activeQuestId);
    }

    // -----------------------------------------------------------------------
    // Internes (utilisent le game directory réel)
    // -----------------------------------------------------------------------

    private PlayerData loadPlayerData(Player player) {
        return loadPlayerData(resolveFilePath(), resolveContextKey(), player.getUUID().toString());
    }

    private Path resolveFilePath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameDirectory == null) {
            return null;
        }
        return mc.gameDirectory.toPath().resolve(FILENAME);
    }

    public String resolveContextKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) {
            return multiplayerContextKey(mc.getCurrentServer().ip);
        }
        if (mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            Path levelId = root.getFileName();
            if (levelId != null) {
                return singleplayerContextKey(levelId.toString());
            }
        }
        return "singleplayer:unknown";
    }

    static String multiplayerContextKey(String hostAndPort) {
        return "multiplayer:" + Objects.requireNonNullElse(hostAndPort, "unknown");
    }

    static String singleplayerContextKey(String levelId) {
        return "singleplayer:" + Objects.requireNonNullElse(levelId, "unknown");
    }

    // -----------------------------------------------------------------------
    // Package-private : permet les tests unitaires sans dépendance Minecraft
    // -----------------------------------------------------------------------

    PlayerData loadPlayerData(Path filePath, String serverKey, String playerKey) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(serverKey, "serverKey");
        Objects.requireNonNull(playerKey, "playerKey");
        if (!Files.exists(filePath)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Map<String, Map<String, PlayerData>> root = GSON.fromJson(reader, DATA_TYPE);
            if (root == null) {
                return null;
            }
            Map<String, PlayerData> serverMap = root.get(serverKey);
            if (serverMap == null) {
                return null;
            }
            return serverMap.get(playerKey);
        } catch (Exception e) {
            backupBrokenFile(filePath, e);
            return null;
        }
    }

    void saveToPath(Path filePath, String serverKey, String playerKey, Set<String> followedQuestIds, String activeQuestId) {
        saveToPath(filePath, serverKey, playerKey, followedQuestIds, Set.of(), activeQuestId);
    }

    void saveToPath(Path filePath, String serverKey, String playerKey, Set<String> followedQuestIds, Set<String> seenQuestIds, String activeQuestId) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(serverKey, "serverKey");
        Objects.requireNonNull(playerKey, "playerKey");
        Objects.requireNonNull(followedQuestIds, "followedQuestIds");
        Objects.requireNonNull(seenQuestIds, "seenQuestIds");

        try {
            Files.createDirectories(filePath.getParent());

            Map<String, Map<String, PlayerData>> root;
            if (Files.exists(filePath)) {
                try (Reader reader = Files.newBufferedReader(filePath)) {
                    root = GSON.fromJson(reader, DATA_TYPE);
                } catch (Exception e) {
                    backupBrokenFile(filePath, e);
                    root = new HashMap<>();
                }
            } else {
                root = new HashMap<>();
            }

            if (root == null) {
                root = new HashMap<>();
            }

            Map<String, PlayerData> serverMap = root.computeIfAbsent(serverKey, k -> new HashMap<>());
            PlayerData data = new PlayerData();
            data.followedQuestIds = new HashSet<>(followedQuestIds);
            data.seenQuestIds = new HashSet<>(seenQuestIds);
            data.activeQuestId = activeQuestId;
            serverMap.put(playerKey, data);

            Path tmpPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmpPath)) {
                GSON.toJson(root, DATA_TYPE, writer);
            }
            try {
                Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            LOGGER.warn("Impossible de sauvegarder les quêtes suivies: {}", e.getMessage());
        }
    }

    Set<String> loadFollowedQuestIds(Path filePath, String serverKey, String playerKey) {
        PlayerData data = loadPlayerData(filePath, serverKey, playerKey);
        return data == null ? Collections.emptySet() : Collections.unmodifiableSet(data.followedQuestIds);
    }

    String loadActiveQuestId(Path filePath, String serverKey, String playerKey) {
        PlayerData data = loadPlayerData(filePath, serverKey, playerKey);
        return data == null ? null : data.activeQuestId;
    }

    Set<String> loadSeenQuestIds(Path filePath, String serverKey, String playerKey) {
        PlayerData data = loadPlayerData(filePath, serverKey, playerKey);
        return data == null || data.seenQuestIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(data.seenQuestIds);
    }

    boolean hasSeenQuestIds(Path filePath, String serverKey, String playerKey) {
        PlayerData data = loadPlayerData(filePath, serverKey, playerKey);
        return data != null && data.seenQuestIds != null;
    }

    private static void backupBrokenFile(Path filePath, Exception exception) {
        Path backup = filePath.resolveSibling(filePath.getFileName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("Fichier de persistance corrompu déplacé vers {} : {}", backup.getFileName(), exception.getMessage());
        } catch (IOException moveFailure) {
            LOGGER.warn("Fichier de persistance corrompu (copie impossible) : {}", exception.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Classe de données pour la sérialisation
    // -----------------------------------------------------------------------

    @SuppressWarnings("FieldMayBeFinal")
    private static final class PlayerData {
        private Set<String> followedQuestIds = new HashSet<>();
        private Set<String> seenQuestIds;
        private String activeQuestId;
    }
}
