package com.cntrident.shulkerboxsort.inventory;

import com.cntrident.shulkerboxsort.planner.PackingPlanner;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public final class ShulkerScanner {
    public static final int PLAYER_INVENTORY_SLOTS = 36;
    public static final int SHULKER_SLOTS = 27;

    private ShulkerScanner() {
    }

    public record ScanResult(List<Integer> stagingInventorySlots,
                             List<PackingPlanner.Box<ItemStackKey>> boxes,
                             int ignoredNamedOrSpecial,
                             int includedMixedFullBoxes,
                             int lockedHomogeneousFullBoxes) {
    }

    public static ScanResult scan(Inventory inventory) {
        List<Integer> stagingSlots = new ArrayList<>();
        List<PackingPlanner.Box<ItemStackKey>> boxes = new ArrayList<>();
        int ignored = 0;
        int full = 0;
        int lockedFull = 0;

        for (int inventorySlot = 0; inventorySlot < PLAYER_INVENTORY_SLOTS; inventorySlot++) {
            ItemStack boxStack = inventory.getItem(inventorySlot);
            if (boxStack.isEmpty()) {
                stagingSlots.add(inventorySlot);
                continue;
            }
            if (!isVanillaShulkerBox(boxStack)) {
                continue;
            }
            if (isNamedOrSpecial(boxStack)) {
                ignored++;
                continue;
            }
            // A non-empty shulker must be a single item. If another mod has
            // made filled boxes stackable, opening one item from that stack is
            // ambiguous and therefore outside the safe automation contract.
            if (boxStack.getCount() != 1) {
                ignored++;
                continue;
            }

            NonNullList<ItemStack> stored = storedItems(boxStack);
            List<PackingPlanner.Stack<ItemStackKey>> slots = new ArrayList<>(SHULKER_SLOTS);
            int occupied = 0;
            boolean capacityFull = true;
            for (int i = 0; i < SHULKER_SLOTS; i++) {
                ItemStack item = i < stored.size() ? stored.get(i) : ItemStack.EMPTY;
                if (item.isEmpty()) {
                    capacityFull = false;
                    slots.add(null);
                } else {
                    occupied++;
                    if (item.getCount() < item.getMaxStackSize()) {
                        capacityFull = false;
                    }
                    slots.add(new PackingPlanner.Stack<>(
                            new ItemStackKey(item), item.getCount(), item.getMaxStackSize()));
                }
            }

            // Empty boxes are only handled by the final inventory merge. A box
            // is considered genuinely full only when all 27 stacks are at
            // their own maximum; 27 occupied but partial stacks must still be
            // eligible for consolidation with matching stacks in other boxes.
            if (occupied == 0) {
                continue;
            }
            if (capacityFull) {
                if (isHomogeneousCapacityFull(slots)) {
                    lockedFull++;
                    continue;
                }
                full++;
            }
            boxes.add(new PackingPlanner.Box<>(inventorySlot, slots));
        }

        return new ScanResult(List.copyOf(stagingSlots), List.copyOf(boxes), ignored, full, lockedFull);
    }

    static <K> boolean isHomogeneousCapacityFull(List<PackingPlanner.Stack<K>> slots) {
        if (slots.isEmpty() || slots.getFirst() == null) {
            return false;
        }
        K firstKey = slots.getFirst().key();
        return slots.stream().allMatch(stack -> stack != null
                && stack.count() == stack.maxCount() && firstKey.equals(stack.key()));
    }

    public static boolean isVanillaShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() == Blocks.SHULKER_BOX;
    }

    public static boolean isNamedOrSpecial(ItemStack stack) {
        return stack.get(DataComponents.CUSTOM_NAME) != null
                || stack.get(DataComponents.CONTAINER_LOOT) != null;
    }

    public static boolean isOrdinaryEmptyShulker(ItemStack stack) {
        if (!isVanillaShulkerBox(stack) || isNamedOrSpecial(stack)) {
            return false;
        }
        return storedItems(stack).stream().allMatch(ItemStack::isEmpty);
    }

    /** Every non-empty ordinary undyed box, including locked homogeneous full boxes. */
    public static List<Integer> sortableNonEmptyBoxSlots(Inventory inventory) {
        List<Integer> result = new ArrayList<>();
        for (int slot = 0; slot < PLAYER_INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isVanillaShulkerBox(stack) && stack.getCount() == 1
                    && !isNamedOrSpecial(stack)
                    && storedItems(stack).stream().anyMatch(item -> !item.isEmpty())) {
                result.add(slot);
            }
        }
        return List.copyOf(result);
    }

    private static NonNullList<ItemStack> storedItems(ItemStack stack) {
        NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY);
        stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }
}
