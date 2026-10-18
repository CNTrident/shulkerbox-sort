package com.cntrident.shulkerboxsort;

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
    private static final int INVENTORY_GUI_WIDTH = 176;
    private static final int BUTTON_RIGHT_INSET = 8;
    private static final int BUTTON_TOP_OFFSET = 3;
    private static Screen buttonScreen;
    private static Button currentButton;

    private InventoryScreenButton() {
    }

    public static void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof InventoryScreen inventoryScreen)) {
            return;
        }

        boolean running = ClientSortController.isRunning();
        int x = buttonX(inventoryScreen);
        int y = buttonY(inventoryScreen);

        Button[] holder = new Button[1];
        Button button = Button.builder(
                        Component.literal(running ? "…" : "⇅"),
                        ignored -> {
                            ClientSortController.start(client);
                            if (ClientSortController.isRunning()) {
                                updateButtonState(holder[0], true);
                            }
                        })
                .pos(x, y)
                .size(BUTTON_SIZE, BUTTON_SIZE)
                .build();
        holder[0] = button;
        updateButtonState(button, running);
        Screens.getWidgets(screen).add(button);
        buttonScreen = screen;
        currentButton = button;
    }

    public static void updatePosition(Minecraft client) {
        if (!(client.gui.screen() instanceof InventoryScreen inventoryScreen)
                || client.gui.screen() != buttonScreen
                || currentButton == null) {
            return;
        }
        currentButton.setX(buttonX(inventoryScreen));
        currentButton.setY(buttonY(inventoryScreen));
    }

    private static int buttonX(InventoryScreen screen) {
        int left = ((AbstractContainerScreenAccessor) screen).shulkerboxSort$getLeftPos();
        return left + INVENTORY_GUI_WIDTH - BUTTON_SIZE - BUTTON_RIGHT_INSET;
    }

    private static int buttonY(InventoryScreen screen) {
        int top = ((AbstractContainerScreenAccessor) screen).shulkerboxSort$getTopPos();
        return top + BUTTON_TOP_OFFSET;
    }

    public static void refresh(Minecraft client) {
        if (client.gui.screen() != buttonScreen || currentButton == null) {
            return;
        }
        boolean running = ClientSortController.isRunning();
        updateButtonState(currentButton, running);
    }

    private static void updateButtonState(Button button, boolean running) {
        button.active = !running;
        button.setMessage(Component.literal(running ? "…" : "⇅"));
        button.setTooltip(Tooltip.create(Component.translatable(running
                ? "gui.shulkerbox_sort.button.running"
                : "gui.shulkerbox_sort.button.sort")));
    }
}
