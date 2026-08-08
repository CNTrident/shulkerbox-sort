package com.cntrident.shulkerboxsort;

import com.cntrident.shulkerboxsort.session.SortSessionManager;
import com.cntrident.shulkerboxsort.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public final class InventoryScreenButton {
    private static final int BUTTON_SIZE = 14;
    private static final int RECIPE_BOOK_BUTTON_X_OFFSET = 104;
    private static final int RECIPE_BOOK_BUTTON_Y_FROM_CENTER = -22;
    private static final int RECIPE_BOOK_BUTTON_WIDTH = 20;
    private static final int RECIPE_BOOK_BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 2;
    private static Screen buttonScreen;
    private static Button currentButton;

    private InventoryScreenButton() {
    }

    public static void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof InventoryScreen inventoryScreen)) {
            return;
        }

        boolean running = SortSessionManager.INSTANCE.isRunning();
        int x = buttonX(inventoryScreen);
        int y = scaledHeight / 2 + RECIPE_BOOK_BUTTON_Y_FROM_CENTER
                + (RECIPE_BOOK_BUTTON_HEIGHT - BUTTON_SIZE) / 2;

        Button[] holder = new Button[1];
        Button button = Button.builder(
                        Component.literal(running ? "…" : "⇅"),
                        ignored -> {
                            SortSessionManager.INSTANCE.start(client);
                            if (SortSessionManager.INSTANCE.isRunning()) {
                                holder[0].active = false;
                                holder[0].setMessage(Component.literal("…"));
                                holder[0].setTooltip(Tooltip.create(Component.translatable(
                                        "gui.shulkerbox_sort.button.running")));
                            }
                        })
                .pos(x, y)
                .size(BUTTON_SIZE, BUTTON_SIZE)
                .build();
        holder[0] = button;
        button.active = !running;
        button.setTooltip(Tooltip.create(Component.translatable(running
                ? "gui.shulkerbox_sort.button.running"
                : "gui.shulkerbox_sort.button.sort")));
        Screens.getWidgets(screen).add(button);
        buttonScreen = screen;
        currentButton = button;
    }

    public static void updatePosition(Minecraft client) {
        if (!(client.screen instanceof InventoryScreen inventoryScreen)
                || client.screen != buttonScreen
                || currentButton == null) {
            return;
        }
        currentButton.setX(buttonX(inventoryScreen));
    }

    private static int buttonX(InventoryScreen screen) {
        int left = ((AbstractContainerScreenAccessor) screen).shulkerboxSort$getLeftPos();
        return left + RECIPE_BOOK_BUTTON_X_OFFSET + RECIPE_BOOK_BUTTON_WIDTH + BUTTON_GAP;
    }

    public static void refresh(Minecraft client) {
        if (client.screen != buttonScreen || currentButton == null) {
            return;
        }
        boolean running = SortSessionManager.INSTANCE.isRunning();
        currentButton.active = !running;
        currentButton.setMessage(Component.literal(running ? "…" : "⇅"));
        currentButton.setTooltip(Tooltip.create(Component.translatable(running
                ? "gui.shulkerbox_sort.button.running"
                : "gui.shulkerbox_sort.button.sort")));
    }
}
