package com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JourneyMapMarkerManagerTest {
    @Test
    void declaresHighDefinitionQuestIconsForJourneyMapMarkers() throws IllegalAccessException {
        assertEquals(new ResourceLocation("cnpcoverlay", "textures/markers/quest_icon-64x.png"),
                value("QUEST_ICON"));
        assertEquals(new ResourceLocation("cnpcoverlay", "textures/markers/side_quest_1-64x.png"),
                value("SIDE_QUEST_ICON"));
        assertEquals(64, value("JOURNEYMAP_ICON_SIZE"));
    }

    private static Object value(String fieldName) throws IllegalAccessException {
        Field field = Arrays.stream(JourneyMapMarkerManager.class.getDeclaredFields())
                .filter(candidate -> candidate.getName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Champ absent : " + fieldName));
        field.setAccessible(true);
        return field.get(null);
    }
}
