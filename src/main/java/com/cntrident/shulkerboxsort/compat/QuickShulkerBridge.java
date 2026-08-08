package com.cntrident.shulkerboxsort.compat;

import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/** Small, isolated bridge to Quick Shulker's public client API. */
public final class QuickShulkerBridge {
    private static final String CLIENT_UTIL = "net.kyrptonaught.quickshulker.client.ClientUtil";
    private static Method checkAndSend;
    private static boolean resolved;

    private QuickShulkerBridge() {
    }

    public static boolean open(ItemStack boxStack, int inventoryMenuSlot) {
        resolve();
        if (checkAndSend == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(checkAndSend.invoke(null, boxStack, inventoryMenuSlot));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> clientUtil = Class.forName(CLIENT_UTIL);
            checkAndSend = clientUtil.getMethod("CheckAndSend", ItemStack.class, int.class);
        } catch (ReflectiveOperationException exception) {
            checkAndSend = null;
        }
    }
}
