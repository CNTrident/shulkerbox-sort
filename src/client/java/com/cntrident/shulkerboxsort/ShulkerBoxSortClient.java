package com.cntrident.shulkerboxsort;

import com.cntrident.shulkerboxsort.network.SortResultPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.slf4j.Logger;

public final class ShulkerBoxSortClient implements ClientModInitializer {
    public static final String MOD_ID = "shulkerbox_sort";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register(InventoryScreenButton::onScreenInit);
        ClientPlayNetworking.registerGlobalReceiver(SortResultPayload.TYPE,
                (payload, context) -> ClientSortController.receive(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientSortController.reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientSortController.tick(client);
            InventoryScreenButton.updatePosition(client);
            InventoryScreenButton.refresh(client);
        });
        LOGGER.info("Shulker Box Sort initialized");
    }
}
