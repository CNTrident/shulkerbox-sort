package com.cntrident.shulkerboxsort.server;

import com.cntrident.shulkerboxsort.inventory.ItemStackKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerInventorySorterTest {
    private static boolean initialized;
    @BeforeAll static void bootstrap() {
        if (initialized) return;
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                .build(lookup)
                .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        // Datagen lookups have no bound tags. Omit the two optional, tag-based
        // variant registries; a running server passes its complete live lookup.
        var categoryLookup = net.minecraft.core.HolderLookup.Provider.create(lookup.listRegistries().filter(registry ->
                !registry.key().equals(net.minecraft.core.registries.Registries.INSTRUMENT)
                        && !registry.key().equals(net.minecraft.core.registries.Registries.PAINTING_VARIANT)));
        DefaultItemOrder.initialize(categoryLookup, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
        initialized = true;
    }
    @Test void fullInventoryNeedsNoBufferAndLooseItemsRemainUntouched() {
        var inv = fullInventory();
        inv.setItem(0, box(stack(Items.REDSTONE_BLOCK, 32)));
        inv.setItem(1, box(stack(Items.REDSTONE_BLOCK, 32), stack(Items.OBSERVER, 64)));
        var before = InventoryTransaction.snapshot(inv);
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 64);
        assertTrue(ItemStack.matches(before.getFirst(), inv.getItem(0)), "Preparation must not mutate inventory");
        assertEquals(1, plan.emptied()); assertEquals(1, plan.remaining());
        plan.transaction().commit(inv);
        for (int i = 2; i < 36; i++) assertTrue(ItemStack.matches(before.get(i), inv.getItem(i)));
        assertEquals(64L, totals(inv).get(new ItemStackKey(stack(Items.REDSTONE_BLOCK, 1))));
    }
    @Test void all36BoxesCanBeFullWithoutAnyStagingSlot() {
        var inv = newInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack[] content = new ItemStack[27];
            for (int i = 0; i < 27; i++) content[i] = stack(i % 2 == 0 ? Items.STONE : Items.OBSERVER, 64);
            inv.setItem(slot, box(content));
        }
        var expected = totals(inv);
        long started = System.nanoTime();
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 1);
        plan.transaction().commit(inv);
        assertEquals(36, plan.sorted()); assertEquals(36, plan.remaining());
        assertEquals(expected, totals(inv));
        System.out.printf(Locale.ROOT, "36 full boxes (972 stacks): server planning and commit %.2f ms%n",
                (System.nanoTime() - started) / 1_000_000.0);
    }
    @Test void overCapacityContainerContentsAreNotTruncated() {
        var inv = newInventory();
        ItemStack[] items = new ItemStack[28]; Arrays.setAll(items, i -> stack(Items.STONE, 1));
        inv.setItem(0, box(items));
        var original = inv.getItem(0).copy();
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 1);
        assertEquals(0, plan.sorted()); plan.transaction().commit(inv);
        assertTrue(ItemStack.matches(original, inv.getItem(0)));
    }
    @Test void componentIdentityAndShellComponentsSurvive() {
        var inv = newInventory();
        ItemStack named = stack(Items.REDSTONE_BLOCK, 20);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Distinct"));
        ItemStack shell = box(named, stack(Items.REDSTONE_BLOCK, 30));
        shell.set(DataComponents.MAX_STACK_SIZE, 64);
        inv.setItem(0, shell); inv.setItem(1, box(named.copyWithCount(10), stack(Items.REDSTONE_BLOCK, 10)));
        var before = totals(inv);
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 64);
        plan.transaction().commit(inv);
        assertEquals(before, totals(inv));
        assertEquals(64, inv.getItem(0).get(DataComponents.MAX_STACK_SIZE));
    }
    @Test void skipsNamedColoredStackedAndHomogeneousFullBoxes() {
        var inv = newInventory();
        var named = box(stack(Items.STONE, 2)); named.set(DataComponents.CUSTOM_NAME, Component.literal("Do not sort"));
        var colored = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse("minecraft:red_shulker_box")));
        assertFalse(colored.isEmpty());
        assertFalse(com.cntrident.shulkerboxsort.inventory.ShulkerScanner.isVanillaShulkerBox(colored));
        colored.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(stack(Items.STONE, 3))));
        var stacked = box(stack(Items.STONE, 4)); stacked.setCount(2);
        ItemStack[] full = new ItemStack[27]; Arrays.setAll(full, i -> stack(Items.STONE, 64));
        inv.setItem(0, named); inv.setItem(1, colored); inv.setItem(2, stacked); inv.setItem(3, box(full));
        var before = InventoryTransaction.snapshot(inv);
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 1);
        assertEquals(0, plan.sorted()); assertEquals(1, plan.remaining());
        plan.transaction().commit(inv);
        for (int i = 0; i < 36; i++) assertTrue(ItemStack.matches(before.get(i), inv.getItem(i)));
    }
    @Test void respectsServerEmptyBoxStackLimitAndExactComponents() {
        for (int limit : new int[]{1, 16, 64}) {
            var inv = newInventory();
            inv.setItem(0, box()); inv.setItem(1, box());
            var special = box(); special.set(DataComponents.MAX_STACK_SIZE, 64); inv.setItem(2, special);
            var plan = ServerInventorySorter.prepare(inv, List.of(), s -> limit);
            assertEquals(limit == 1, plan.emptyBoxStackingDisabled());
            plan.transaction().commit(inv);
            assertEquals(limit == 1 ? 1 : 2, inv.getItem(0).getCount());
            assertEquals(1, inv.getItem(2).getCount());
        }
    }
    @Test void staleInventoryIsRejectedBeforeAnyWrite() {
        var inv = newInventory();
        inv.setItem(0, box(stack(Items.STONE, 32))); inv.setItem(1, box(stack(Items.STONE, 32)));
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 64);
        inv.setItem(35, stack(Items.DIAMOND, 1));
        var changed = InventoryTransaction.snapshot(inv);
        assertThrows(IllegalStateException.class, () -> plan.transaction().commit(inv));
        for (int i = 0; i < 36; i++) assertTrue(ItemStack.matches(changed.get(i), inv.getItem(i)));
    }
    @Test void disabledStackingStillCompletesPackingAndKeepsEmptyBoxesSeparate() {
        var inv = newInventory();
        for (int i = 0; i < 3; i++) inv.setItem(i, box(stack(Items.STONE, 32)));
        inv.setItem(3, box());
        var expected = totals(inv);
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 1);
        assertTrue(plan.emptyBoxStackingDisabled());
        assertEquals(2, plan.emptied()); assertEquals(1, plan.remaining()); assertEquals(0, plan.merged());
        plan.transaction().commit(inv);
        assertEquals(expected, totals(inv));
        int emptyBoxes = 0;
        for (int i = 0; i < 36; i++) {
            var box = inv.getItem(i);
            if (com.cntrident.shulkerboxsort.inventory.ShulkerScanner.isOrdinaryEmptyShulker(box)) {
                emptyBoxes++; assertEquals(1, box.getCount());
            }
        }
        assertEquals(3, emptyBoxes);
    }
    @Test void checksDefaultRuleWhenThereAreNoEmptyBoxes() {
        for (int limit : new int[]{1, 64}) {
            var inv = newInventory();
            inv.setItem(0, box(stack(Items.STONE, 64)));
            var plan = ServerInventorySorter.prepare(inv, List.of(), s -> limit);
            assertEquals(limit == 1, plan.emptyBoxStackingDisabled());
            plan.transaction().commit(inv);
            assertEquals(64L, totals(inv).get(new ItemStackKey(stack(Items.STONE, 1))));
        }
    }
    @Test void componentEnabledEmptyBoxesDoNotTriggerGlobalDisabledWarning() {
        var inv = newInventory();
        inv.setItem(0, box());
        var componentEnabled = box(); componentEnabled.set(DataComponents.MAX_STACK_SIZE, 64);
        inv.setItem(1, componentEnabled);
        var plan = ServerInventorySorter.prepare(inv, List.of(), ItemStack::getMaxStackSize);
        assertFalse(plan.emptyBoxStackingDisabled());
        plan.transaction().commit(inv);
        assertEquals(1, inv.getItem(0).getCount()); assertEquals(1, inv.getItem(1).getCount());
    }
    @Test void refusesInventoryPlansThatLoseItems() {
        var inv = newInventory(); inv.setItem(0, box(stack(Items.STONE, 32)));
        var before = InventoryTransaction.snapshot(inv); var after = new ArrayList<>(before);
        after.set(0, box());
        assertThrows(IllegalStateException.class, () -> new InventoryTransaction(before, after));
    }
    @Test void failedWriteRollsBackOriginalInventory() {
        var inv = new FailingContainer();
        inv.setItem(0, box(stack(Items.STONE, 32))); inv.setItem(1, box(stack(Items.STONE, 32)));
        var before = InventoryTransaction.snapshot(inv);
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 64);
        inv.failNext = true;
        assertThrows(IllegalStateException.class, () -> plan.transaction().commit(inv));
        for (int i = 0; i < 36; i++) assertTrue(ItemStack.matches(before.get(i), inv.getItem(i)));
    }
    @Test void thirdPartyRollbackRefusalIsReportedAsRecoveryFailure() {
        var inv = new Inventory(null, new EntityEquipment()) {
            boolean armed; boolean failing;
            @Override public void setItem(int slot, ItemStack stack) {
                if (armed && slot == 1) { failing = true; throw new IllegalStateException("Commit blocked"); }
                if (failing && slot == 0) throw new IllegalStateException("Recovery blocked");
                super.setItem(slot, stack);
            }
        };
        inv.setItem(0, box(stack(Items.STONE, 32))); inv.setItem(1, box(stack(Items.STONE, 32)));
        var plan = ServerInventorySorter.prepare(inv, List.of(), s -> 1);
        inv.armed = true;
        assertThrows(InventoryTransaction.RecoveryFailedException.class, () -> plan.transaction().commit(inv));
    }
    @Test void legacyPreferenceReferencesCannotOverrideServerDefaultOrInjectItems() {
        var inv = newInventory();
        inv.setItem(0, box(stack(Items.APPLE, 64), stack(Items.STONE, 64)));
        var expected = totals(inv);
        var plan = ServerInventorySorter.prepare(inv, List.of(0, 0, 900, 1), s -> 1);
        plan.transaction().commit(inv);
        assertEquals(expected, totals(inv));
        assertTrue(inv.getItem(0).get(DataComponents.CONTAINER).allItemsCopyStream().findFirst().orElseThrow().is(Items.STONE));
    }
    @Test void randomInventoriesConserveComponentsAndHaveNoFurtherCompaction() {
        var random = new Random(31);
        for (int trial = 0; trial < 40; trial++) {
            var inv = fullInventory();
            for (int slot = 0; slot < 8; slot++) {
                var items = new ItemStack[27];
                for (int i = 0; i < 27; i++) {
                    items[i] = random.nextBoolean() ? ItemStack.EMPTY : stack(
                            switch (random.nextInt(4)) {
                                case 0 -> Items.REDSTONE_BLOCK; case 1 -> Items.OBSERVER;
                                case 2 -> Items.ENDER_PEARL; default -> Items.DIAMOND_SWORD;
                            }, 1);
                    if (!items[i].isEmpty()) items[i].setCount(1 + random.nextInt(items[i].getMaxStackSize()));
                    if (!items[i].isEmpty() && random.nextInt(5) == 0)
                        items[i].set(DataComponents.CUSTOM_NAME, Component.literal("variant"));
                }
                inv.setItem(slot, box(items));
            }
            var expected = totals(inv);
            var first = ServerInventorySorter.prepare(inv, List.of(), s -> 64);
            first.transaction().commit(inv);
            assertEquals(expected, totals(inv));
            var second = ServerInventorySorter.prepare(inv, List.of(), s -> 64);
            assertEquals(0, second.emptied(), "No further empty holes should emerge after server packing");
        }
    }
    private static Inventory newInventory() { return new Inventory(null, new EntityEquipment()); }
    private static Inventory fullInventory() {
        var inv = newInventory();
        for (int i = 0; i < 36; i++) inv.setItem(i, stack(Items.DIRT, 64));
        return inv;
    }
    private static ItemStack stack(net.minecraft.world.item.Item item, int count) { return new ItemStack(item, count); }
    private static ItemStack box(ItemStack... items) {
        var result = new ItemStack(Items.SHULKER_BOX);
        result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(Arrays.asList(items)));
        return result;
    }
    private static Map<ItemStackKey, Long> totals(Container inv) {
        var result = new HashMap<ItemStackKey, Long>();
        for (int i = 0; i < 36; i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            var contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) contents.nonEmptyItemCopyStream().forEach(s ->
                    result.merge(new ItemStackKey(s), (long) s.getCount() * stack.getCount(), Long::sum));
            else result.merge(new ItemStackKey(stack), (long) stack.getCount(), Long::sum);
        }
        return result;
    }
    private static final class FailingContainer extends Inventory {
        boolean failNext;
        FailingContainer() { super(null, new EntityEquipment()); }
        @Override public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            if (failNext) { failNext = false; throw new IllegalStateException("Simulated failure after write"); }
        }
    }
}
