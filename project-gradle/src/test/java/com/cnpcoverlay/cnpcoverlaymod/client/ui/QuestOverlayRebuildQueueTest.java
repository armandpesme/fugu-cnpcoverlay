package com.cnpcoverlay.cnpcoverlaymod.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuestOverlayRebuildQueueTest {
    @Test
    void requestsStayDeferredUntilPolledAndPrepareRequestsAreCoalesced() {
        QuestOverlayRebuildQueue queue = new QuestOverlayRebuildQueue();

        queue.request(false);
        queue.request(true);
        assertTrue(queue.pending());

        QuestOverlayRebuildQueue.Request request = queue.poll();
        assertTrue(request.prepare());
        assertFalse(queue.pending());
        assertNull(queue.poll());
    }
}
