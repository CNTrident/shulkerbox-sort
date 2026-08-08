package com.cntrident.shulkerboxsort.session;

import com.cntrident.shulkerboxsort.InventoryScreenButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class SortSessionManager {
    public static final SortSessionManager INSTANCE = new SortSessionManager();

    private SortSession active;

    private SortSessionManager() {
    }

    public boolean isRunning() {
        return active != null;
    }

    public void start(Minecraft client) {
        if (active != null) {
            return;
        }
        active = SortSession.create(client);
        if (active != null) {
            active.enterBackgroundMode(client);
        }
    }

    public boolean consumeExpectedContainerScreen(Screen screen) {
        return active != null && active.consumeExpectedContainerScreen(screen);
    }

    public void tick(Minecraft client) {
        InventoryScreenButton.updatePosition(client);
        if (active == null) {
            return;
        }
        if (!active.tick(client)) {
            active = null;
            InventoryScreenButton.refresh(client);
        }
    }
}
