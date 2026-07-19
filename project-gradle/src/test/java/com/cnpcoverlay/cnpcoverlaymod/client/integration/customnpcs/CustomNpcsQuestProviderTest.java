package com.cnpcoverlay.cnpcoverlaymod.client.integration.customnpcs;

import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class CustomNpcsQuestProviderTest {
    @Test
    void discoverySupportsTheLegacyRuntimeQuestContract() {
        assertDoesNotThrow(() -> {
            Object access = discover(LegacyQuest.class);
            Field completer = accessor(access, "completer");
            Field questInterface = accessor(access, "questInterface");
            assertEquals("completerNpc", completer.getName());
            assertEquals("questInterface", questInterface.getName());
            Method objectives = accessor(access, "getObjectives");
            assertEquals("getObjectives", objectives.getName());
            assertEquals(LegacyQuestInterface.class, objectives.getDeclaringClass());
        });
    }

    @Test
    void discoveryDoesNotRequireCompleterMetadata() {
        assertDoesNotThrow(() -> {
            Object access = discover(QuestWithoutCompleter.class);
            assertNotNull(access);
            assertNull(accessor(access, "completer"));
        });
    }

    @Test
    void discoveryMatchesTheCustomNpcsJarShippedWithTheWorkspace() {
        assertDoesNotThrow(() -> {
            Path jar = Path.of("..", "docs", "mods", "jar",
                    "CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260711.jar").toAbsolutePath();
            assertEquals(true, Files.isRegularFile(jar));
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
                Class<?> controller = Class.forName("noppes.npcs.controllers.PlayerQuestController", false, loader);
                Class<?> quest = Class.forName("noppes.npcs.controllers.data.Quest", false, loader);
                Object access = CustomNpcsQuestProvider.Access.discover(controller, quest);
                Field completer = accessor(access, "completer");
                Method objectives = accessor(access, "getObjectives");
                assertEquals("completerNpc", completer.getName());
                assertEquals("noppes.npcs.quests.QuestInterface", objectives.getDeclaringClass().getName());
            }
        });
    }

    private static Object discover(Class<?> questClass) throws Exception {
        Class<?> accessClass = Arrays.stream(CustomNpcsQuestProvider.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("Access"))
                .findFirst().orElseThrow();
        Method discover = accessClass.getDeclaredMethod("discover", Class.class, Class.class);
        discover.setAccessible(true);
        return discover.invoke(null, LegacyController.class, questClass);
    }

    @SuppressWarnings("unchecked")
    private static <T> T accessor(Object access, String name) throws Exception {
        Method method = access.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (T) method.invoke(access);
    }

    public static final class LegacyController {
        public static List<Object> getActiveQuests(Player player) {
            return List.of();
        }

        public static boolean isQuestCompleted(Player player, int questId) {
            return false;
        }
    }

    public static final class LegacyQuest {
        public int id;
        public String title;
        public Object category;
        public String logText;
        public String completerNpc;
        public LegacyQuestInterface questInterface = new LegacyQuestInterface();
    }

    public static final class QuestWithoutCompleter {
        public int id;
        public String title;
        public Object category;
        public String logText;
        public LegacyQuestInterface questInterface = new LegacyQuestInterface();
    }

    public static final class LegacyQuestInterface {
        public Object[] getObjectives(Player player) {
            return new Object[0];
        }
    }
}
