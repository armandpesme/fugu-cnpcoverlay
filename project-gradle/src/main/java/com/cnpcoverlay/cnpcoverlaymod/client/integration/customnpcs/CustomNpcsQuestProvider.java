package com.cnpcoverlay.cnpcoverlaymod.client.integration.customnpcs;

import com.cnpcoverlay.cnpcoverlaymod.client.integration.QuestProvider;
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
import java.util.List;

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
                         Field logText, Field questInterface, Field completer, Method getObjectives) {
        static Access discover(Class<?> controller, Class<?> quest) throws ReflectiveOperationException {
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
                    getObjectives);
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
