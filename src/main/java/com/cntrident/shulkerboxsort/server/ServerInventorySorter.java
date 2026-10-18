package com.cntrident.shulkerboxsort.server;

import com.cntrident.shulkerboxsort.inventory.ShulkerScanner;
import com.cntrident.shulkerboxsort.planner.GlobalSortPlanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.*;
import java.util.function.ToIntFunction;

public final class ServerInventorySorter {
    private ServerInventorySorter() {}
    public record Prepared(InventoryTransaction transaction, int sorted, int emptied,
                           int remaining, int merged, int skipped, boolean emptyBoxStackingDisabled) {}
    public static Prepared prepare(Container inventory, List<Integer> ignoredLegacyOrder,
                                   ToIntFunction<ItemStack> emptyLimit) {
        List<ItemStack> before = InventoryTransaction.snapshot(inventory);
        var detached = new SimpleContainer(before.stream().map(ItemStack::copy).toArray(ItemStack[]::new));
        var scan = ShulkerScanner.scan(detached);
        // The server owns the sorting rule. Legacy wire preferences are ignored
        // so commands and all client versions receive the same default layout.
        var plan = GlobalSortPlanner.planLayouts(scan.boxes(), DefaultItemOrder.current());
        for (var box : plan.targets()) {
            var items = new ArrayList<ItemStack>(27);
            for (var stack : box.slots()) items.add(stack == null ? ItemStack.EMPTY
                    : stack.key().template().copyWithCount(stack.count()));
            ItemStack updated = before.get(box.inventorySlot()).copy();
            // Keep an explicit empty CONTAINER: vanilla shulker prototypes use it,
            // and identical empty boxes can now merge without artificial mismatch.
            updated.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            detached.setItem(box.inventorySlot(), updated);
        }
        EmptyMergeResult merged = mergeEmptyBoxes(detached, emptyLimit);
        var after = InventoryTransaction.snapshot(detached);
        return new Prepared(new InventoryTransaction(before, after), scan.boxes().size(),
                plan.emptiedBoxes(), plan.remainingNonEmptyBoxes() + scan.lockedHomogeneousFullBoxes(),
                merged.freedSlots(), scan.ignoredNamedOrSpecial(), merged.stackingDisabled());
    }
    private record EmptyMergeResult(int freedSlots, boolean stackingDisabled) {}
    private static EmptyMergeResult mergeEmptyBoxes(Container inventory, ToIntFunction<ItemStack> emptyLimit) {
        int merged = 0;
        boolean foundEmpty = false;
        boolean stackingEnabled = false;
        for (int target = 0; target < 36; target++) {
            ItemStack to = inventory.getItem(target);
            if (!ShulkerScanner.isOrdinaryEmptyShulker(to)) continue;
            foundEmpty = true;
            int limit = Math.max(1, Math.min(99, emptyLimit.applyAsInt(to)));
            stackingEnabled |= limit > 1;
            // A limit of one is a supported setting, not a failed sort.
            if (limit == 1) continue;
            for (int source = target + 1; source < 36 && to.getCount() < limit; source++) {
                ItemStack from = inventory.getItem(source);
                if (!ShulkerScanner.isOrdinaryEmptyShulker(from)
                        || !ItemStack.isSameItemSameComponents(to, from)) continue;
                int count = Math.min(from.getCount(), limit - to.getCount());
                to.grow(count); from.shrink(count);
                if (from.isEmpty()) { inventory.setItem(source, ItemStack.EMPTY); merged++; }
            }
        }
        // With no empty boxes, still report the ordinary server rule. When
        // components allow an actual empty box to stack, do not claim that
        // stacking is globally disabled just because the prototype cannot.
        boolean disabled = foundEmpty ? !stackingEnabled
                : emptyLimit.applyAsInt(new ItemStack(Items.SHULKER_BOX)) <= 1;
        return new EmptyMergeResult(merged, disabled);
    }
}
