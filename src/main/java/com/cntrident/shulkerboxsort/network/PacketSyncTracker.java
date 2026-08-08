package com.cntrident.shulkerboxsort.network;

import java.util.HashMap;
import java.util.Map;

public final class PacketSyncTracker {
    private static long revision;
    private static final Map<Integer, Update> updates = new HashMap<>();
    private static final Map<Integer, Long> contentRevisions = new HashMap<>();

    private record Update(long revision, int stateId) {
    }

    private PacketSyncTracker() {
    }

    public static synchronized void recordSlot(int containerId, int stateId) {
        revision++;
        updates.put(containerId, new Update(revision, stateId));
    }

    public static synchronized void recordContent(int containerId, int stateId) {
        revision++;
        updates.put(containerId, new Update(revision, stateId));
        contentRevisions.put(containerId, revision);
    }

    public static synchronized long revision() {
        return revision;
    }

    public static synchronized boolean advancedFor(long previousRevision, int containerId) {
        Update update = updates.get(containerId);
        return update != null && update.revision() > previousRevision;
    }

    public static synchronized boolean contentAdvancedFor(long previousRevision, int containerId) {
        return contentRevisions.getOrDefault(containerId, Long.MIN_VALUE) > previousRevision;
    }

    public static synchronized boolean confirmedAfter(long previousRevision, int containerId,
                                                       int previousStateId, int currentStateId) {
        Update update = updates.get(containerId);
        return update != null
                && update.revision() > previousRevision
                && update.stateId() != previousStateId
                && update.stateId() == currentStateId;
    }
}
