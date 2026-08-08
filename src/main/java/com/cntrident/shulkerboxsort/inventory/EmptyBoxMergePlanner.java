package com.cntrident.shulkerboxsort.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class EmptyBoxMergePlanner {
    private EmptyBoxMergePlanner() {
    }

    public record Merge(int sourceInventorySlot, int targetInventorySlot,
                        ItemStackKey key, int amount) {
    }

    public static List<Merge> plan(Inventory inventory) {
        List<Merge> merges = new ArrayList<>();
        ItemStack[] simulated = new ItemStack[ShulkerScanner.PLAYER_INVENTORY_SLOTS];
        for (int i = 0; i < simulated.length; i++) {
            simulated[i] = inventory.getItem(i).copy();
        }

        for (int target = 0; target < simulated.length; target++) {
            ItemStack targetStack = simulated[target];
            if (!ShulkerScanner.isOrdinaryEmptyShulker(targetStack)) {
                continue;
            }
            // This feature is explicitly for environments where ordinary
            // empty shulkers are stackable. Carpet-style implementations and
            // Item Scroller use 64 as the effective empty-box limit even when
            // the vanilla item prototype still reports one client-side.
            int max = 64;
            if (max <= targetStack.getCount()) {
                continue;
            }

            for (int source = target + 1; source < simulated.length; source++) {
                ItemStack sourceStack = simulated[source];
                if (!ShulkerScanner.isOrdinaryEmptyShulker(sourceStack)
                        || !ItemStack.isSameItemSameComponents(targetStack, sourceStack)) {
                    continue;
                }
                int amount = Math.min(sourceStack.getCount(), max - targetStack.getCount());
                merges.add(new Merge(source, target, new ItemStackKey(sourceStack), amount));
                targetStack.grow(amount);
                sourceStack.shrink(amount);
                if (sourceStack.isEmpty()) {
                    simulated[source] = ItemStack.EMPTY;
                }
                if (targetStack.getCount() >= max) {
                    break;
                }
            }
        }
        return List.copyOf(merges);
    }
}
