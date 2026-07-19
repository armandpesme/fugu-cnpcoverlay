package com.cnpcoverlay.cnpcoverlaymod.client.integration.customnpcs;

import com.cnpcoverlay.cnpcoverlaymod.client.integration.QuestProvider;
import com.cnpcoverlay.cnpcoverlaymod.client.integration.QuestProvider.FinishedQuestStamps;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestObjectiveSnapshot;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestSnapshot;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès réfléchi isolé à CustomNPCs. Le contrat minimal suit le JAR runtime
 * 1.20.1.20260711 tout en tolérant les variantes plus récentes des sources.
 */
public final class CustomNpcsQuestProvider implements QuestProvider {
    private static final String MODID = "customnpcs";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DISCOVERY_RETRY_MS = 5_000L;
    private static final ClassValue<ObjectiveMethods> OBJECTIVE_METHODS = new ClassValue<>() {
        @Override
        protected ObjectiveMethods computeValue(Class<?> type) {
            try {
                return ObjectiveMethods.discover(type);
            } catch (ReflectiveOperationException exception) {
                throw new ObjectiveDiscoveryException(exception);
            }
        }
    };

    private volatile Access access;
    private volatile long nextDiscoveryAttemptMs;
    private String lastDiscoveryError = "";
    private String lastReadError = "";

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded(MODID) && access() != null;
    }

    @Override
    public List<QuestSnapshot> getActiveQuests(Player player) {
        Access api = access();
        if (player == null || api == null) {
            return List.of();
        }
        try {
            Object active = api.getActiveQuests().invoke(null, player);
            if (!(active instanceof Iterable<?> quests)) {
                warnReadOnce("getActiveQuests n'a pas renvoyé une collection");
                return List.of();
            }
            List<QuestSnapshot> snapshots = new ArrayList<>();
            for (Object quest : quests) {
                if (quest == null) {
                    continue;
                }
                try {
                    snapshots.add(snapshot(api, quest, player));
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    warnReadOnce("Quête CustomNPCs ignorée : " + rootMessage(exception));
                }
            }
            return List.copyOf(snapshots);
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnReadOnce("Lecture des quêtes CustomNPCs impossible : " + rootMessage(exception));
            return List.of();
        }
    }

    @Override
    public Object getFinishedQuestIdentity(Player player) {
        Access api = access();
        if (player == null || api == null) {
            return FinishedQuestStamps.class;
        }
        try {
            return api.finishedQuestIdentity(player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnReadOnce("Lecture du miroir des quêtes terminées impossible : " + rootMessage(exception));
            return FinishedQuestStamps.class;
        }
    }

    @Override
    public FinishedQuestStamps getFinishedQuestStamps(Player player) {
        Access api = access();
        if (player == null || api == null) {
            return FinishedQuestStamps.unavailable();
        }
        try {
            return api.finishedQuestStamps(player);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            warnReadOnce("Lecture des quêtes terminées CustomNPCs impossible : " + rootMessage(exception));
            return FinishedQuestStamps.unavailable();
        }
    }

    private QuestSnapshot snapshot(Access api, Object quest, Player player) throws ReflectiveOperationException {
        int id = api.id().getInt(quest);
        String title = string(api.title().get(quest));
        String category = nestedTitle(api.category().get(quest));
        String logText = string(api.logText().get(quest));
        String completer = readCompleter(api.completer(), quest);
        Object questInterface = api.questInterface().get(quest);
        Object rawObjectives = questInterface == null ? null : api.getObjectives().invoke(questInterface, player);
        List<QuestObjectiveSnapshot> objectives = objectives(rawObjectives);
        boolean completed = invokeBoolean(api.isQuestCompleted(), null, player, id);

        float total = 0.0f;
        int measured = 0;
        for (QuestObjectiveSnapshot objective : objectives) {
            if (objective.maximum() > 0) {
                total += Math.min(1.0f, (float) objective.current() / objective.maximum());
                measured++;
            }
        }
        float progress = measured > 0
                ? total / measured
                : (objectives.isEmpty() ? 0.0f : (float) objectives.stream().filter(QuestObjectiveSnapshot::completed).count() / objectives.size());
        return new QuestSnapshot(String.valueOf(id), category, title, logText, objectives, progress, completed, completer);
    }

    private List<QuestObjectiveSnapshot> objectives(Object rawObjectives) {
        if (rawObjectives == null || !rawObjectives.getClass().isArray()) {
            return List.of();
        }
        List<QuestObjectiveSnapshot> result = new ArrayList<>();
        for (int index = 0; index < Array.getLength(rawObjectives); index++) {
            Object objective = Array.get(rawObjectives, index);
            if (objective == null) {
                continue;
            }
            try {
                ObjectiveMethods methods = OBJECTIVE_METHODS.get(objective.getClass());
                String text = methods.text(objective);
                int current = methods.progress(objective);
                int maximum = methods.maximum(objective);
                boolean completed = methods.completed(objective);
                result.add(new QuestObjectiveSnapshot(index + 1, text, current, maximum, completed));
            } catch (ObjectiveDiscoveryException | ReflectiveOperationException exception) {
                warnReadOnce("Objectif CustomNPCs partiellement indisponible : " + rootMessage(exception));
                result.add(new QuestObjectiveSnapshot(index + 1, "Objectif " + (index + 1), 0, 0, false));
            }
        }
        return List.copyOf(result);
    }

    private Access access() {
        Access cached = access;
        if (cached != null) {
            return cached;
        }
        if (!ModList.get().isLoaded(MODID) || System.currentTimeMillis() < nextDiscoveryAttemptMs) {
            return null;
        }
        try {
            Access found = Access.discover(
                    Class.forName("noppes.npcs.controllers.PlayerQuestController"),
                    Class.forName("noppes.npcs.controllers.data.Quest"));
            access = found;
            lastDiscoveryError = "";
            LOGGER.info("Intégration CustomNPCs active ({})", found.completer() == null ? "sans métadonnée completer" : found.completer().getName());
            return found;
        } catch (ReflectiveOperationException | LinkageError exception) {
            nextDiscoveryAttemptMs = System.currentTimeMillis() + DISCOVERY_RETRY_MS;
            String message = rootMessage(exception);
            if (!message.equals(lastDiscoveryError)) {
                lastDiscoveryError = message;
                LOGGER.warn("Intégration CustomNPCs temporairement indisponible : {}", message);
            }
            return null;
        }
    }

    private void warnReadOnce(String message) {
        if (!message.equals(lastReadError)) {
            lastReadError = message;
            LOGGER.warn(message);
        }
    }

    private static String readCompleter(Field field, Object quest) throws ReflectiveOperationException {
        if (field == null) {
            return "";
        }
        Object value = field.get(quest);
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            Object name = value.getClass().getMethod("getName").invoke(value);
            return name instanceof Component component ? component.getString() : string(name);
        } catch (NoSuchMethodException ignored) {
            return string(value);
        }
    }

    private static String nestedTitle(Object category) {
        if (category == null) {
            return "";
        }
        try {
            return string(category.getClass().getField("title").get(category));
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    private static boolean invokeBoolean(Method method, Object target, Object... arguments) throws ReflectiveOperationException {
        Object value = method.invoke(target, arguments);
        return value instanceof Boolean result && result;
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        if (root instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            root = invocation.getCause();
        } else if (root instanceof ObjectiveDiscoveryException discovery && discovery.getCause() != null) {
            root = discovery.getCause();
        }
        return root.getClass().getSimpleName() + ": " + String.valueOf(root.getMessage());
    }

    static record Access(Method getActiveQuests, Method isQuestCompleted, Field id, Field title, Field category,
                         Field logText, Field questInterface, Field completer, Method getObjectives,
                         Method getPlayerData, Field questData, Method getFinishedQuest,
                         Method getFinishedTime, Field finishedQuests) {
        static Access discover(Class<?> controller, Class<?> quest) throws ReflectiveOperationException {
            Access activeAccess = discoverActive(controller, quest);
            try {
                Class<?> playerData = Class.forName(
                        "noppes.npcs.controllers.data.PlayerData",
                        false,
                        controller.getClassLoader()
                );
                return activeAccess.withHistory(playerData);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                return activeAccess.withoutHistory(exception);
            }
        }

        static Access discover(Class<?> controller, Class<?> quest, Class<?> playerData)
                throws ReflectiveOperationException {
            Access activeAccess = discoverActive(controller, quest);
            try {
                return activeAccess.withHistory(playerData);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                return activeAccess.withoutHistory(exception);
            }
        }

        private static Access discoverActive(Class<?> controller, Class<?> quest)
                throws ReflectiveOperationException {
            Field questInterface = quest.getField("questInterface");
            Method getObjectives = questInterface.getType().getMethod("getObjectives", Player.class);
            getObjectives.setAccessible(true);
            return new Access(
                    controller.getMethod("getActiveQuests", Player.class),
                    controller.getMethod("isQuestCompleted", Player.class, int.class),
                    quest.getField("id"),
                    quest.getField("title"),
                    quest.getField("category"),
                    quest.getField("logText"),
                    questInterface,
                    optionalField(quest, "completerNpc", "completer"),
                    getObjectives,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        private Access withHistory(Class<?> playerData) throws ReflectiveOperationException {
            Method getPlayerData = playerData.getMethod("get", Player.class);
            Field questData = field(playerData, "questData");
            Class<?> questDataType = questData.getType();
            Method getFinishedQuest = optionalMethod(questDataType, "getFinishedQuest");
            Method getFinishedTime = optionalMethod(questDataType, "getFinishedTime", int.class);
            Field finishedQuests = optionalDeclaredField(questDataType, "finishedQuests");
            if (finishedQuests == null && (getFinishedQuest == null || getFinishedTime == null)) {
                throw new NoSuchFieldException("finishedQuests/getFinishedQuest/getFinishedTime");
            }
            return new Access(
                    getActiveQuests,
                    isQuestCompleted,
                    id,
                    title,
                    category,
                    logText,
                    questInterface,
                    completer,
                    getObjectives,
                    getPlayerData,
                    questData,
                    getFinishedQuest,
                    getFinishedTime,
                    finishedQuests);
        }

        private Access withoutHistory(Throwable exception) {
            LOGGER.warn(
                    "Historique CustomNPCs indisponible, quêtes actives conservées : {}",
                    rootMessage(exception)
            );
            return this;
        }

        FinishedQuestStamps finishedQuestStamps(Player player) throws ReflectiveOperationException {
            Object playerQuestData = playerQuestData(player);
            if (playerQuestData == null) {
                return FinishedQuestStamps.unavailable();
            }
            Object identity = finishedQuestIdentity(playerQuestData);
            Map<String, Long> stamps;
            if (getFinishedQuest != null && getFinishedTime != null) {
                stamps = stampsFromAccessors(playerQuestData);
            } else if (identity instanceof Map<?, ?>) {
                stamps = stampsFromMap(identity);
            } else {
                return FinishedQuestStamps.unavailable();
            }
            return new FinishedQuestStamps(identity, stamps);
        }

        Object finishedQuestIdentity(Player player) throws ReflectiveOperationException {
            Object playerQuestData = playerQuestData(player);
            return playerQuestData == null ? FinishedQuestStamps.class : finishedQuestIdentity(playerQuestData);
        }

        private Object playerQuestData(Player player) throws ReflectiveOperationException {
            if (getPlayerData == null || questData == null) {
                return null;
            }
            Object playerData = getPlayerData.invoke(null, player);
            return playerData == null ? null : questData.get(playerData);
        }

        private Object finishedQuestIdentity(Object playerQuestData) throws IllegalAccessException {
            return finishedQuests == null ? playerQuestData : finishedQuests.get(playerQuestData);
        }

        private Map<String, Long> stampsFromAccessors(Object playerQuestData)
                throws ReflectiveOperationException {
            Object ids = getFinishedQuest.invoke(playerQuestData);
            Map<String, Long> stamps = new LinkedHashMap<>();
            if (ids instanceof Iterable<?> iterable) {
                for (Object id : iterable) {
                    addAccessorStamp(stamps, playerQuestData, id);
                }
            } else if (ids != null && ids.getClass().isArray()) {
                for (int index = 0; index < Array.getLength(ids); index++) {
                    addAccessorStamp(stamps, playerQuestData, Array.get(ids, index));
                }
            }
            return stamps;
        }

        private void addAccessorStamp(Map<String, Long> stamps, Object playerQuestData, Object rawId)
                throws ReflectiveOperationException {
            if (rawId instanceof Number id) {
                Object rawStamp = getFinishedTime.invoke(playerQuestData, id.intValue());
                if (rawStamp instanceof Number stamp) {
                    stamps.put(String.valueOf(id.intValue()), stamp.longValue());
                }
            }
        }

        private static Map<String, Long> stampsFromMap(Object rawMap) {
            if (!(rawMap instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Long> stamps = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof Number id && entry.getValue() instanceof Number stamp) {
                    stamps.put(String.valueOf(id.intValue()), stamp.longValue());
                }
            }
            return stamps;
        }

        private static Field optionalField(Class<?> type, String... names) {
            for (String name : names) {
                try {
                    return type.getField(name);
                } catch (NoSuchFieldException ignored) {
                    // Variante CustomNPCs suivante.
                }
            }
            return null;
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            try {
                return type.getField(name);
            } catch (NoSuchFieldException exception) {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
        }

        private static Field optionalDeclaredField(Class<?> type, String name) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                return null;
            }
        }

        private static Method optionalMethod(Class<?> type, String name, Class<?>... parameterTypes) {
            try {
                Method method;
                try {
                    method = type.getMethod(name, parameterTypes);
                } catch (NoSuchMethodException exception) {
                    method = type.getDeclaredMethod(name, parameterTypes);
                }
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    private record ObjectiveMethods(Method text, Method mcText, Method progress, Method maximum, Method completed) {
        static ObjectiveMethods discover(Class<?> type) throws ReflectiveOperationException {
            return new ObjectiveMethods(method(type, "getText"), method(type, "getMCText"), method(type, "getProgress"),
                    method(type, "getMaxProgress"), method(type, "isCompleted"));
        }

        String text(Object target) throws ReflectiveOperationException {
            Object component = mcText.invoke(target);
            if (component instanceof Component textComponent && !textComponent.getString().isBlank()) {
                return textComponent.getString();
            }
            return string(text.invoke(target));
        }

        int progress(Object target) throws ReflectiveOperationException {
            return ((Number) progress.invoke(target)).intValue();
        }

        int maximum(Object target) throws ReflectiveOperationException {
            return ((Number) maximum.invoke(target)).intValue();
        }

        boolean completed(Object target) throws ReflectiveOperationException {
            return invokeBoolean(completed, target);
        }

        private static Method method(Class<?> type, String name) throws ReflectiveOperationException {
            Method method;
            try {
                method = type.getMethod(name);
            } catch (NoSuchMethodException exception) {
                method = type.getDeclaredMethod(name);
            }
            method.setAccessible(true);
            return method;
        }
    }

    private static final class ObjectiveDiscoveryException extends RuntimeException {
        private ObjectiveDiscoveryException(Throwable cause) {
            super(cause);
        }
    }
}
