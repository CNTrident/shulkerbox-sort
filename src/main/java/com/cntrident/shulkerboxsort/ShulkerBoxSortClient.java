package com.cntrident.shulkerboxsort;

import com.cntrident.shulkerboxsort.session.SortSessionManager;
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
        ClientTickEvents.END_CLIENT_TICK.register(SortSessionManager.INSTANCE::tick);
        LOGGER.info("Shulker Box Sort initialized");
    }
}
