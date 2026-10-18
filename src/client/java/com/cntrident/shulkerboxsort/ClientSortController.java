package com.cntrident.shulkerboxsort;

import com.cntrident.shulkerboxsort.network.SortRequestPayload;
import com.cntrident.shulkerboxsort.network.SortResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class ClientSortController {
    private static int nextId;
    private static int pendingId = -1;
    private static long deadline;
    private ClientSortController() {}
    public static boolean isRunning() { return pendingId != -1; }
    public static void reset() { pendingId = -1; }
    public static void start(Minecraft client) {
        if (isRunning() || client.player == null) return;
        if (!ClientPlayNetworking.canSend(SortRequestPayload.TYPE)) {
            tell(client, "message.shulkerbox_sort.server_missing"); return;
        }
        if (!client.player.containerMenu.getCarried().isEmpty()) {
            tell(client, "message.shulkerbox_sort.cursor_not_empty"); return;
        }
        pendingId = ++nextId;
        if (pendingId == -1) pendingId = ++nextId;
        deadline = System.nanoTime() + 10_000_000_000L;
        ClientPlayNetworking.send(new SortRequestPayload(pendingId, List.of()));
    }
    public static void receive(Minecraft client, SortResultPayload result) {
        if (pendingId != result.requestId()) return;
        reset();
        tell(client, result.messageKey(), result.sorted(), result.emptied(), result.remaining(),
                result.merged(), String.format(Locale.ROOT, "%.2f", result.micros() / 1000.0));
    }
    public static void tick(Minecraft client) {
        if (isRunning() && System.nanoTime() >= deadline) {
            reset(); tell(client, "message.shulkerbox_sort.response_timeout");
        }
    }
    private static void tell(Minecraft client, String key, Object... args) {
        if (client.player != null) client.gui.chatListener().handleSystemMessage(Component.translatable(key, args), false);
    }
}
