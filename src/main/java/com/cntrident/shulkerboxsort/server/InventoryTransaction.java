package com.cntrident.shulkerboxsort.server;

import com.cntrident.shulkerboxsort.inventory.ItemStackKey;
import com.cntrident.shulkerboxsort.inventory.ShulkerScanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.*;

/** Detached inventory snapshots with conservation verification and rollback. */
public final class InventoryTransaction {
    public static final class RecoveryFailedException extends IllegalStateException {
        public RecoveryFailedException(RuntimeException cause) {
            super("A third-party inventory hook prevented rollback", cause);
        }
    }
    private final List<ItemStack> before;
    private final List<ItemStack> after;
    public InventoryTransaction(List<ItemStack> before, List<ItemStack> after) {
        if (before.size() != 36 || after.size() != 36) throw new IllegalArgumentException("Expected 36 slots");
        this.before = copy(before); this.after = copy(after);
        if (!totals(this.before).equals(totals(this.after)))
            throw new IllegalStateException("Inventory conservation failed");
    }
    public static List<ItemStack> snapshot(Container inventory) {
        var result = new ArrayList<ItemStack>(36);
        for (int i = 0; i < 36; i++) result.add(inventory.getItem(i).copy());
        return result;
    }
    public List<ItemStack> result() { return copy(after); }
    public void commit(Container inventory) {
        for (int i = 0; i < 36; i++) {
            if (!ItemStack.matches(before.get(i), inventory.getItem(i)))
                throw new IllegalStateException("Inventory changed before commit");
        }
        try {
            for (int i = 0; i < 36; i++) {
                if (!ItemStack.matches(before.get(i), after.get(i))) inventory.setItem(i, after.get(i).copy());
            }
            for (int i = 0; i < 36; i++) {
                if (!ItemStack.matches(after.get(i), inventory.getItem(i)))
                    throw new IllegalStateException("Container altered the committed stack");
            }
            inventory.setChanged();
        } catch (RuntimeException failure) {
            for (int i = 0; i < 36; i++) {
                try { inventory.setItem(i, before.get(i).copy()); }
                catch (RuntimeException restoreError) { failure.addSuppressed(restoreError); }
            }
            try { inventory.setChanged(); }
            catch (RuntimeException restoreError) { failure.addSuppressed(restoreError); }
            for (int i = 0; i < 36; i++) {
                if (!ItemStack.matches(before.get(i), inventory.getItem(i)))
                    throw new RecoveryFailedException(failure);
            }
            throw failure;
        }
    }
    private static List<ItemStack> copy(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }
    // Count shells separately from their contents: changing CONTAINER is allowed,
    // changing any other component (name, stack limit, custom data...) is not.
    private static Map<ItemStackKey, Long> totals(List<ItemStack> inventory) {
        var totals = new HashMap<ItemStackKey, Long>();
        for (ItemStack stack : inventory) {
            if (stack.isEmpty()) continue;
            if (!ShulkerScanner.isVanillaShulkerBox(stack)) { add(totals, stack, stack.getCount()); continue; }
            ItemStack shell = stack.copy(); shell.remove(DataComponents.CONTAINER);
            add(totals, shell, stack.getCount());
            stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                    .nonEmptyItemCopyStream().forEach(item -> add(totals, item,
                            (long) item.getCount() * stack.getCount()));
        }
        return totals;
    }
    private static void add(Map<ItemStackKey, Long> totals, ItemStack stack, long count) {
        totals.merge(new ItemStackKey(stack), count, Long::sum);
    }
}
