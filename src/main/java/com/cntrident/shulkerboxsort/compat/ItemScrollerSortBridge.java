package com.cntrident.shulkerboxsort.compat;

import com.cntrident.shulkerboxsort.inventory.ItemStackKey;
import com.cntrident.shulkerboxsort.planner.PackingPlanner;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;

/** Reflection bridge for Item Scroller's configured comparator and real sorter. */
public final class ItemScrollerSortBridge {
    private static final Method COMPARE_STACKS = findComparator();
    private static final Method SORT_INVENTORY = findSortInventory();
    private static final Field PENDING_SORT_TASK = findPendingSortTask();

    private ItemScrollerSortBridge() {
    }

    public static boolean isAvailable() {
        return COMPARE_STACKS != null;
    }

    public static boolean isSorterAvailable() {
        return SORT_INVENTORY != null;
    }

    public static void sortInventory(AbstractContainerScreen<?> screen) {
        if (SORT_INVENTORY == null) {
            throw new IllegalStateException("Item Scroller inventory sorter is unavailable");
        }
        try {
            SORT_INVENTORY.invoke(null, screen);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Item Scroller inventory sorting failed", exception);
        }
    }

    /** True while Item Scroller is waiting for its server-sync pong callback. */
    public static boolean isSortPending() {
        if (PENDING_SORT_TASK == null) {
            return false;
        }
        try {
            return PENDING_SORT_TASK.get(null) != null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to inspect Item Scroller sort state", exception);
        }
    }

    public static Comparator<PackingPlanner.Stack<ItemStackKey>> comparator() {
        if (COMPARE_STACKS == null) {
            throw new IllegalStateException("Item Scroller stack comparator is unavailable");
        }
        return (left, right) -> compare(toItemStack(left), toItemStack(right));
    }

    private static ItemStack toItemStack(PackingPlanner.Stack<ItemStackKey> stack) {
        ItemStack result = stack.key().template();
        result.setCount(stack.count());
        return result;
    }

    private static int compare(ItemStack left, ItemStack right) {
        try {
            return (int) COMPARE_STACKS.invoke(null, left, right);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Item Scroller stack comparison failed", exception);
        }
    }

    private static Method findComparator() {
        try {
            Class<?> inventoryUtils = Class.forName("fi.dy.masa.itemscroller.util.InventoryUtils");
            Method method = inventoryUtils.getDeclaredMethod("compareStacks", ItemStack.class, ItemStack.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static Method findSortInventory() {
        try {
            Class<?> inventoryUtils = Class.forName("fi.dy.masa.itemscroller.util.InventoryUtils");
            Method method = inventoryUtils.getDeclaredMethod(
                    "sortInventory", AbstractContainerScreen.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static Field findPendingSortTask() {
        try {
            Class<?> inventoryUtils = Class.forName("fi.dy.masa.itemscroller.util.InventoryUtils");
            Field field = inventoryUtils.getDeclaredField("selectedSlotUpdateTask");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }
}
