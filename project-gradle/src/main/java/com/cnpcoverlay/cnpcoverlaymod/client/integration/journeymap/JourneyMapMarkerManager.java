package com.cnpcoverlay.cnpcoverlaymod.client.integration.journeymap;

import com.cnpcoverlay.cnpcoverlaymod.CnpcOverlayMod;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestMarkerPlanner;
import com.mojang.logging.LogUtils;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Displayable;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Implémentation v2 de JourneyMap, isolée du cœur afin de préserver la dépendance optionnelle. */
public final class JourneyMapMarkerManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int JOURNEYMAP_ICON_SIZE = 64;
    private static final ResourceLocation QUEST_ICON = new ResourceLocation(CnpcOverlayMod.MODID, "textures/markers/quest_icon-64x.png");
    private static final ResourceLocation SIDE_QUEST_ICON = new ResourceLocation(CnpcOverlayMod.MODID, "textures/markers/side_quest_1-64x.png");
    private static final JourneyMapMarkerManager INSTANCE = new JourneyMapMarkerManager();
    private final Map<String, RenderedMarker> activeMarkers = new LinkedHashMap<>();
    private IClientAPI api;

    private JourneyMapMarkerManager() {}

    public static JourneyMapMarkerManager get() {
        return INSTANCE;
    }

    public void initialize(IClientAPI clientApi) {
        api = clientApi;
    }

    /** Diff déterministe : aucune recréation lorsque l'intention métier est identique. */
    public void synchronize(List<QuestMarkerPlanner.DesiredMarker> desiredMarkers) {
        if (api == null) {
            return;
        }
        Map<String, QuestMarkerPlanner.DesiredMarker> desired = new LinkedHashMap<>();
        for (QuestMarkerPlanner.DesiredMarker marker : desiredMarkers) {
            desired.put(marker.id(), marker);
        }
        activeMarkers.entrySet().removeIf(entry -> {
            if (desired.containsKey(entry.getKey())) {
                return false;
            }
            remove(entry.getValue());
            return true;
        });
        for (QuestMarkerPlanner.DesiredMarker marker : desired.values()) {
            String fingerprint = fingerprint(marker);
            RenderedMarker existing = activeMarkers.get(marker.id());
            if (existing != null && existing.fingerprint.equals(fingerprint)) {
                continue;
            }
            if (existing != null) {
                remove(existing);
            }
            RenderedMarker rendered = create(marker, fingerprint);
            if (rendered != null) {
                activeMarkers.put(marker.id(), rendered);
            }
        }
    }

    public void removeAllMarkers() {
        for (RenderedMarker marker : activeMarkers.values()) {
            remove(marker);
        }
        activeMarkers.clear();
        if (api != null) {
            api.removeAll(CnpcOverlayMod.MODID);
        }
    }

    private RenderedMarker create(QuestMarkerPlanner.DesiredMarker marker, String fingerprint) {
        try {
            ResourceKey<Level> dimension = dimension(marker.dimensionId());
            List<Displayable> displayables = new ArrayList<>();
            if (marker.type() == QuestMarkerPlanner.MarkerType.RADIUS) {
                PolygonOverlay overlay = new PolygonOverlay(CnpcOverlayMod.MODID, dimension,
                        new ShapeProperties().setStrokeColor(marker.color()).setStrokeOpacity(0.75f)
                                .setFillColor(marker.color()).setFillOpacity(0.20f).setStrokeWidth(2.0f),
                        circle(marker));
                overlay.setTitle(marker.tooltip());
                api.show(overlay);
                displayables.add(overlay);
            } else {
                ResourceLocation icon = marker.type() == QuestMarkerPlanner.MarkerType.TURN_IN ? QUEST_ICON : SIDE_QUEST_ICON;
                MapImage image = new MapImage(icon, JOURNEYMAP_ICON_SIZE, JOURNEYMAP_ICON_SIZE).centerAnchors().setBlur(false);
                MarkerOverlay overlay = new MarkerOverlay(CnpcOverlayMod.MODID, new BlockPos(marker.x(), marker.y(), marker.z()), image);
                overlay.setDimension(dimension);
                overlay.setTitle(marker.tooltip());
                overlay.setLabel(marker.type() == QuestMarkerPlanner.MarkerType.TURN_IN ? "?" : "!" + marker.objectiveIndex());
                api.show(overlay);
                displayables.add(overlay);
            }
            return new RenderedMarker(fingerprint, displayables);
        } catch (Exception exception) {
            LOGGER.warn("Impossible d'afficher le marqueur JourneyMap {} : {}", marker.id(), exception.toString());
            return null;
        }
    }

    private void remove(RenderedMarker marker) {
        if (api != null) {
            marker.displayables.forEach(api::remove);
        }
    }

    private static ResourceKey<Level> dimension(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return ResourceKey.create(Registries.DIMENSION, location == null ? Level.OVERWORLD.location() : location);
    }

    private static MapPolygon circle(QuestMarkerPlanner.DesiredMarker marker) {
        List<BlockPos> points = new ArrayList<>();
        for (int step = 0; step < 16; step++) {
            double angle = Math.PI * 2.0 * step / 16.0;
            points.add(new BlockPos(marker.x() + (int) Math.round(Math.cos(angle) * marker.radius()), marker.y(),
                    marker.z() + (int) Math.round(Math.sin(angle) * marker.radius())));
        }
        return new MapPolygon(points);
    }

    private static String fingerprint(QuestMarkerPlanner.DesiredMarker marker) {
        return marker.type() + ":" + marker.x() + ':' + marker.y() + ':' + marker.z() + ':' + marker.radius() + ':'
                + marker.color() + ':' + marker.dimensionId() + ':' + marker.tooltip();
    }

    private record RenderedMarker(String fingerprint, List<Displayable> displayables) {}
}
