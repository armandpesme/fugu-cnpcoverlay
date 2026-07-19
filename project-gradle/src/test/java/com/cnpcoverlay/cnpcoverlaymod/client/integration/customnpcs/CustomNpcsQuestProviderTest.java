package com.cnpcoverlay.cnpcoverlaymod.client.integration.customnpcs;

import com.cnpcoverlay.cnpcoverlaymod.client.integration.QuestProvider.FinishedQuestStamps;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomNpcsQuestProviderTest {
    @Test
    void activeDiscoverySurvivesAMissingPlayerDataClass() throws Exception {
        CustomNpcsQuestProvider.Access access = CustomNpcsQuestProvider.Access.discover(
                LegacyController.class,
                LegacyQuest.class
        );

        assertActiveContractAndUnavailableHistory(access);
    }

    @Test
    void activeDiscoverySurvivesAPlayerDataClassWithoutHistoryContract() throws Exception {
        CustomNpcsQuestProvider.Access access = CustomNpcsQuestProvider.Access.discover(
                LegacyController.class,
                LegacyQuest.class,
                PlayerDataWithoutHistoryFixture.class
        );

        assertActiveContractAndUnavailableHistory(access);
    }

    @Test
    void finishedQuestStampsSupportThePublicRuntimeMap() throws Exception {
        CustomNpcsQuestProvider.Access access = finishedQuestAccess(PlayerDataFixture.class);
        FinishedQuestStamps result = access.finishedQuestStamps(null);

        assertEquals(Map.of("7", 91L), result.stamps());
        assertSame(PlayerDataFixture.INSTANCE.questData.finishedQuests, result.identityToken());
        assertSame(result.identityToken(), access.finishedQuestIdentity(null));
    }

    @Test
    void finishedQuestStampsSupportProtectedMapAccessors() throws Exception {
        CustomNpcsQuestProvider.Access access = finishedQuestAccess(PlayerDataProtectedFixture.class);
        FinishedQuestStamps result = access.finishedQuestStamps(null);

        assertEquals(Map.of("7", 91L), result.stamps());
        assertSame(PlayerDataProtectedFixture.INSTANCE.questData.identityToken(), result.identityToken());
        assertSame(result.identityToken(), access.finishedQuestIdentity(null));
    }

    @Test
    void readableEmptyFinishedQuestMapIsAvailable() throws Exception {
        CustomNpcsQuestProvider.Access access = finishedQuestAccess(PlayerDataEmptyFixture.class);

        FinishedQuestStamps result = access.finishedQuestStamps(null);

        assertTrue(result.available());
        assertTrue(result.stamps().isEmpty());
        assertSame(PlayerDataEmptyFixture.INSTANCE.questData.finishedQuests, result.identityToken());
    }

    @Test
    void missingPlayerQuestDataIsUnavailable() throws Exception {
        CustomNpcsQuestProvider.Access access = finishedQuestAccess(PlayerDataUnavailableFixture.class);

        FinishedQuestStamps result = access.finishedQuestStamps(null);

        assertFalse(result.available());
        assertTrue(result.stamps().isEmpty());
    }

    @Test
    void nullFinishedQuestMapWithoutAccessorsIsUnavailable() throws Exception {
        CustomNpcsQuestProvider.Access access = finishedQuestAccess(PlayerDataNullMapFixture.class);

        FinishedQuestStamps result = access.finishedQuestStamps(null);

        assertFalse(result.available());
        assertTrue(result.stamps().isEmpty());
    }

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
        Method discover = accessClass.getDeclaredMethod("discover", Class.class, Class.class, Class.class);
        discover.setAccessible(true);
        return discover.invoke(null, LegacyController.class, questClass, PlayerDataFixture.class);
    }

    private static CustomNpcsQuestProvider.Access finishedQuestAccess(Class<?> playerDataClass) throws Exception {
        return CustomNpcsQuestProvider.Access.discover(
                LegacyController.class,
                LegacyQuest.class,
                playerDataClass
        );
    }

    private static void assertActiveContractAndUnavailableHistory(CustomNpcsQuestProvider.Access access)
            throws Exception {
        assertEquals(List.of(), access.getActiveQuests().invoke(null, new Object[]{null}));
        assertSame(FinishedQuestStamps.class, access.finishedQuestIdentity(null));
        assertFalse(access.finishedQuestStamps(null).available());
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

    public static final class PlayerDataFixture {
        public static final PlayerDataFixture INSTANCE = new PlayerDataFixture();
        public final QuestDataPublic questData = new QuestDataPublic();

        public static PlayerDataFixture get(Player player) {
            return INSTANCE;
        }
    }

    public static final class QuestDataPublic {
        public final Map<Integer, Long> finishedQuests = new HashMap<>(Map.of(7, 91L));
    }

    public static final class PlayerDataEmptyFixture {
        public static final PlayerDataEmptyFixture INSTANCE = new PlayerDataEmptyFixture();
        public final QuestDataEmpty questData = new QuestDataEmpty();

        public static PlayerDataEmptyFixture get(Player player) {
            return INSTANCE;
        }
    }

    public static final class QuestDataEmpty {
        public final Map<Integer, Long> finishedQuests = new HashMap<>();
    }

    public static final class PlayerDataUnavailableFixture {
        public final QuestDataPublic questData = null;

        public static PlayerDataUnavailableFixture get(Player player) {
            return new PlayerDataUnavailableFixture();
        }
    }

    public static final class PlayerDataNullMapFixture {
        public final QuestDataNullMap questData = new QuestDataNullMap();

        public static PlayerDataNullMapFixture get(Player player) {
            return new PlayerDataNullMapFixture();
        }
    }

    public static final class QuestDataNullMap {
        public final Map<Integer, Long> finishedQuests = null;
    }

    public static final class PlayerDataWithoutHistoryFixture {
    }

    public static final class PlayerDataProtectedFixture {
        public static final PlayerDataProtectedFixture INSTANCE = new PlayerDataProtectedFixture();
        public final QuestDataProtected questData = new QuestDataProtected();

        public static PlayerDataProtectedFixture get(Player player) {
            return INSTANCE;
        }
    }

    public static final class QuestDataProtected {
        protected final Map<Integer, Long> finishedQuests = new HashMap<>(Map.of(7, 91L));

        public Set<Integer> getFinishedQuest() {
            return finishedQuests.keySet();
        }

        public long getFinishedTime(int questId) {
            return finishedQuests.getOrDefault(questId, 0L);
        }

        Object identityToken() {
            return finishedQuests;
        }
    }
}
