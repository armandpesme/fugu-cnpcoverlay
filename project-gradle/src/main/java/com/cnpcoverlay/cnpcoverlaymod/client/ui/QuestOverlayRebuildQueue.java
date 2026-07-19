package com.cnpcoverlay.cnpcoverlaymod.client.ui;

/**
 * Agrège les demandes de reconstruction jusqu'au prochain tick écran.
 */
final class QuestOverlayRebuildQueue {
    private boolean pending;
    private boolean prepare;

    void request(boolean prepareView) {
        pending = true;
        prepare |= prepareView;
    }

    boolean pending() {
        return pending;
    }

    Request poll() {
        if (!pending) {
            return null;
        }
        Request request = new Request(prepare);
        pending = false;
        prepare = false;
        return request;
    }

    record Request(boolean prepare) {
    }
}
