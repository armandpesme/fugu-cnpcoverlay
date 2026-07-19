package com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class QuestMapMetadataTest {

    @Test
    void nullOrBlankReturnsEmpty() {
        assertTrue(QuestMapMetadata.parse(null).isEmpty());
        assertTrue(QuestMapMetadata.parse("").isEmpty());
        assertTrue(QuestMapMetadata.parse("   ").isEmpty());
    }

    @Test
    void singleObjectiveMarker() {
        String logText = "#!1-7896,4566,4563,12\nAller secourir les Riri";
        QuestMapMetadata meta = QuestMapMetadata.parse(logText);
        assertEquals(1, meta.objectiveMarkers().size());
        var m = meta.objectiveMarkers().get(0);
        assertEquals(1, m.objectiveIndex());
        assertEquals(7896, m.x());
        assertEquals(4566, m.z());
        assertEquals(4563, m.y());
        assertEquals(12, m.radius());
        assertTrue(meta.turnInMarker().isEmpty());
    }

    @Test
    void multipleObjectivesSameLine() {
        String logText = "#!1-7896,4566,4563,12#!2-7816,4576,4363,01#!3-7746,4236,0063,01\nObjectif triple";
        QuestMapMetadata meta = QuestMapMetadata.parse(logText);
        assertEquals(3, meta.objectiveMarkers().size());
        assertEquals(1, meta.objectiveMarkers().get(0).objectiveIndex());
        assertEquals(2, meta.objectiveMarkers().get(1).objectiveIndex());
        assertEquals(3, meta.objectiveMarkers().get(2).objectiveIndex());
    }

    @Test
    void turnInMarker() {
        String logText = "#?-4562,4457,2410\nRendre la quête";
        QuestMapMetadata meta = QuestMapMetadata.parse(logText);
        assertTrue(meta.objectiveMarkers().isEmpty());
        assertTrue(meta.turnInMarker().isPresent());
        var t = meta.turnInMarker().get();
        assertEquals(-4562, t.x());
        assertEquals(4457, t.z());
        assertEquals(2410, t.y());
    }

    @Test
    void bothObjectiveAndTurnIn() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#!1-100,200,300,10\n#?-500,600,700\nMixte");
        assertEquals(1, meta.objectiveMarkers().size());
        assertTrue(meta.turnInMarker().isPresent());
    }

    @Test
    void objectiveAndTurnInSameLine() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#!1-100,200,300,10#?-500,600,700\nMixte");
        assertEquals(1, meta.objectiveMarkers().size());
        assertTrue(meta.turnInMarker().isPresent());
    }

    @Test
    void invalidEntryDoesNotCrash() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#!invalid\n#!1-100,200,300,10\nNormal text");
        assertEquals(1, meta.objectiveMarkers().size());
    }

    @Test
    void nonMetadataLinesAreIgnored() {
        QuestMapMetadata meta = QuestMapMetadata.parse("Normal description line\nAnother line\n#!1-10,20,30,5\n# final line");
        assertEquals(1, meta.objectiveMarkers().size());
    }

    @Test
    void emptyMetadataAfterHashIsIgnored() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#\n# \n  #hidden\n#!1-10,20,30,5\nVisible");
        assertEquals(1, meta.objectiveMarkers().size());
    }

    @Test
    void coordinatesFormatXZY() {
        var m = QuestMapMetadata.parse("#!1-100,200,300,5").objectiveMarkers().get(0);
        assertEquals(100, m.x(), "X doit être 100");
        assertEquals(200, m.z(), "Z doit être 200 (2e valeur)");
        assertEquals(300, m.y(), "Y doit être 300 (3e valeur)");
    }

    @Test
    void turnInWithExtraPartsAfterThird() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#?-100,200,300,extra,stuff\nTurn in");
        assertTrue(meta.turnInMarker().isPresent());
        assertEquals(-100, meta.turnInMarker().get().x());
    }

    @Test
    void objectiveWithWhitespaceInCoords() {
        var m = QuestMapMetadata.parse("#!1- 100 , 200 , 300 , 5").objectiveMarkers().get(0);
        assertEquals(100, m.x());
        assertEquals(200, m.z());
        assertEquals(300, m.y());
        assertEquals(5, m.radius());
    }

    @Test
    void noMarkersReturnsEmpty() {
        assertTrue(QuestMapMetadata.parse("Description simple\nsans métadonnées").isEmpty());
    }

    @Test
    void multipleObjectivesAcrossLines() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#!1-100,200,300,10\n#!2-400,500,600,20\n#!3-700,800,900,30");
        assertEquals(3, meta.objectiveMarkers().size());
    }

    @Test
    void singleHashLineOnly() {
        assertTrue(QuestMapMetadata.parse("#").isEmpty());
        assertTrue(QuestMapMetadata.parse("# ").isEmpty());
    }

    @Test
    void emptyToString() {
        QuestMapMetadata empty = QuestMapMetadata.empty();
        assertTrue(empty.isEmpty());
        assertTrue(empty.objectiveMarkers().isEmpty());
        assertTrue(empty.turnInMarker().isEmpty());
    }

    @Test
    void negativeCoordinateInObjective() {
        // #!1--1269,2534,105,10 : le second tiret fait partie du nombre négatif
        var m = QuestMapMetadata.parse("#!1--1269,2534,105,10").objectiveMarkers().get(0);
        assertEquals(1, m.objectiveIndex());
        assertEquals(-1269, m.x());
        assertEquals(2534, m.z());
        assertEquals(105, m.y());
        assertEquals(10, m.radius());
    }

    @Test
    void multipleNegativeCoordinates() {
        // --100 = -100, -200 = -200, etc.
        QuestMapMetadata meta = QuestMapMetadata.parse("#!1--100,-200,300,5#!2-400,-500,-600,8");
        assertEquals(2, meta.objectiveMarkers().size());
        assertEquals(-100, meta.objectiveMarkers().get(0).x());
        assertEquals(-200, meta.objectiveMarkers().get(0).z());
        assertEquals(-600, meta.objectiveMarkers().get(1).y());
    }

    @Test
    void rejectsInvalidIndexAndNegativeRadiusAndKeepsTheLastDuplicate() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#!0-1,2,3,4#!1-10,20,30,-1#!1-100,200,300,5#!1-400,500,600,6");

        assertEquals(1, meta.objectiveMarkers().size());
        var marker = meta.objectiveMarkers().get(0);
        assertEquals(1, marker.objectiveIndex());
        assertEquals(400, marker.x());
        assertEquals(6, marker.radius());
    }

    @Test
    void rejectsOverflowWithoutDroppingOtherMarkers() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#!1-999999999999,2,3,4#!2-10,20,30,5");

        assertEquals(1, meta.objectiveMarkers().size());
        assertEquals(2, meta.objectiveMarkers().get(0).objectiveIndex());
    }

    @Test
    void mixedSignWithTurnIn() {
        // --100 = index=1,x=-100 ; #?-4562 = turnIn x=-4562
        QuestMapMetadata meta = QuestMapMetadata.parse("#!1--100,-200,300,5\n#?-4562,4457,2410");
        assertEquals(1, meta.objectiveMarkers().size());
        assertEquals(-100, meta.objectiveMarkers().get(0).x());
        assertEquals(-200, meta.objectiveMarkers().get(0).z());
        assertTrue(meta.turnInMarker().isPresent());
        assertEquals(-4562, meta.turnInMarker().get().x());
    }

    @Test
    void turnInWithExplicitDelimiterAndNegativeX() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#?--1235,4457,2410");

        assertTrue(meta.turnInMarker().isPresent());
        assertEquals(-1235, meta.turnInMarker().get().x());
        assertEquals(4457, meta.turnInMarker().get().z());
        assertEquals(2410, meta.turnInMarker().get().y());
    }

    @Test
    void turnInWithOptionalIndexAndNegativeX() {
        QuestMapMetadata meta = QuestMapMetadata.parse("#?1--1235,4457,2410");

        assertTrue(meta.turnInMarker().isPresent());
        assertEquals(-1235, meta.turnInMarker().get().x());
    }
}
