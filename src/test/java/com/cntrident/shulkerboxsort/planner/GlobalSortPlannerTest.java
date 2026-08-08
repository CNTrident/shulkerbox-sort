package com.cntrident.shulkerboxsort.planner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalSortPlannerTest {
    @Test
    void mergesThenSortsAndPreservesTotals() {
        List<PackingPlanner.Box<String>> input = List.of(
                box(2, stack("z", 20, 64), stack("a", 1, 1), null),
                box(8, stack("z", 50, 64), null, null)
        );
        var plan = GlobalSortPlanner.plan(input, java.util.Comparator.comparing(PackingPlanner.Stack::key));

        assertEquals(totals(input), totals(plan.targets()));
        assertEquals(1, plan.emptiedBoxes());
        assertEquals("a", firstNonEmptyPage(plan, 0).key());
        assertTrue(plan.swapCycles().stream().allMatch(cycle ->
                cycle.steps().getFirst().registerBefore() == null
                        && cycle.steps().getLast().registerAfter() == null));
    }

    @Test
    void assignsAnEarlyPageToTheLaterBoxThatAlreadyOwnsItsItems() {
        List<PackingPlanner.Box<String>> input = List.of(
                box(0, stack("z", 64, 64), stack("z", 64, 64)),
                box(20, stack("a", 64, 64), stack("a", 64, 64))
        );
        var plan = GlobalSortPlanner.plan(input, java.util.Comparator.comparing(PackingPlanner.Stack::key));

        assertEquals(1, plan.pageForBox().get(0));
        assertEquals(0, plan.pageForBox().get(1));
        assertNotEquals(List.of(0, 1), plan.pageForBox());
        assertTrue(plan.swapCycles().isEmpty());
    }

    @Test
    void fullPermutationUsesAClosedRegisterCycle() {
        List<PackingPlanner.Box<String>> input = List.of(
                box(0, stack("b", 1, 1), stack("a", 1, 1), stack("c", 1, 1))
        );
        var plan = GlobalSortPlanner.plan(input, java.util.Comparator.comparing(PackingPlanner.Stack::key));

        assertEquals(1, plan.swapCycles().size());
        assertEquals(3, plan.swapCycles().getFirst().steps().size());
        assertEquals("a", plan.targets().getFirst().slots().getFirst().key());
    }

    @Test
    void stableComparatorKeepsFirstOccurrenceOrder() {
        var plan = GlobalSortPlanner.plan(List.of(
                box(0, stack("first", 1, 1), stack("second", 1, 1))
        ), (left, right) -> 0);
        assertEquals("first", plan.targets().getFirst().slots().get(0).key());
        assertEquals("second", plan.targets().getFirst().slots().get(1).key());
    }

    @Test
    void randomizedPlansExecuteExactlyAndConserveEveryItem() {
        java.util.Random random = new java.util.Random(0x5A17B0L);
        for (int iteration = 0; iteration < 200; iteration++) {
            List<PackingPlanner.Box<String>> input = new ArrayList<>();
            int boxCount = 1 + random.nextInt(5);
            int boxSize = 2 + random.nextInt(5);
            for (int box = 0; box < boxCount; box++) {
                List<PackingPlanner.Stack<String>> slots = new ArrayList<>();
                for (int slot = 0; slot < boxSize; slot++) {
                    if (random.nextInt(4) == 0) {
                        slots.add(null);
                    } else {
                        int item = random.nextInt(6);
                        int max = item % 2 == 0 ? 16 : 64;
                        slots.add(stack("item-" + item, 1 + random.nextInt(max), max));
                    }
                }
                input.add(new PackingPlanner.Box<>(box * 3, slots));
            }

            var plan = GlobalSortPlanner.plan(input,
                    java.util.Comparator.<PackingPlanner.Stack<String>, String>comparing(PackingPlanner.Stack::key)
                            .thenComparingInt(PackingPlanner.Stack::count));
            assertEquals(totals(input), totals(plan.targets()));
            assertEquals(plan.targets(), execute(input, plan));
        }
    }

    @Test
    void bulkGroupsAreAllocatedBeforeSmallItemsCanCutThemAcrossPages() {
        List<PackingPlanner.Stack<String>> stacks = new ArrayList<>();
        add(stacks, "a-small", 10);
        add(stacks, "observer", 12);
        add(stacks, "redstone", 10);
        var plan = GlobalSortPlanner.plan(boxesFromStacks(27, 2, stacks),
                java.util.Comparator.comparing(PackingPlanner.Stack::key));

        assertEquals(1, boxesContaining(plan.targets(), "observer"));
        assertEquals(1, boxesContaining(plan.targets(), "redstone"));
        assertEquals(totals(boxesFromStacks(27, 2, stacks)), totals(plan.targets()));
    }

    @Test
    void moreThanOnePageOfABulkItemCreatesAHomogeneousFullPageFirst() {
        List<PackingPlanner.Stack<String>> stacks = new ArrayList<>();
        add(stacks, "redstone", 30);
        add(stacks, "observer", 6);
        var plan = GlobalSortPlanner.plan(boxesFromStacks(27, 2, stacks),
                java.util.Comparator.comparing(PackingPlanner.Stack::key));

        assertTrue(plan.targets().stream().anyMatch(box -> box.slots().stream()
                .allMatch(stack -> stack != null && stack.key().equals("redstone"))));
        assertEquals(2, boxesContaining(plan.targets(), "redstone"));
    }

    @Test
    void earlyPartialStackSpanningTwoBoxesIsFilledDirectlyFromTheTail() {
        List<PackingPlanner.Stack<String>> stacks = new ArrayList<>();
        stacks.add(stack("redstone", 32, 64));
        add(stacks, "redstone", 27);
        var plan = GlobalSortPlanner.plan(boxesFromStacks(27, 2, stacks),
                java.util.Comparator.comparingInt(PackingPlanner.Stack::count));

        assertEquals(1, plan.merges().size(), "the remainder must not bubble through 27 slots");
        assertEquals(32, plan.merges().getFirst().amount());
        assertTrue(plan.targets().stream().anyMatch(box -> box.slots().stream()
                .allMatch(stack -> stack != null
                        && stack.key().equals("redstone") && stack.count() == 64)));
        assertEquals(totals(boxesFromStacks(27, 2, stacks)), totals(plan.targets()));
    }

    @Test
    void partialStackThatFitsInOneBoxIsNotMovedOnlyForCountOrder() {
        List<PackingPlanner.Box<String>> input = List.of(
                box(0, stack("redstone", 32, 64), stack("redstone", 64, 64), null));
        var plan = GlobalSortPlanner.plan(input,
                java.util.Comparator.<PackingPlanner.Stack<String>>comparingInt(
                        PackingPlanner.Stack::count).reversed());

        assertTrue(plan.merges().isEmpty());
        assertTrue(plan.swapCycles().isEmpty());
        assertEquals(32, plan.targets().getFirst().slots().get(0).count());
        assertEquals(64, plan.targets().getFirst().slots().get(1).count());
    }

    @Test
    void fewerThanSixStacksPerItemUsesOriginalSequentialPartitioning() {
        List<PackingPlanner.Stack<String>> stacks = new ArrayList<>();
        add(stacks, "a", 4);
        add(stacks, "b", 4);
        add(stacks, "c", 2);
        var plan = GlobalSortPlanner.plan(boxesFromStacks(5, 2, stacks),
                java.util.Comparator.comparing(PackingPlanner.Stack::key));
        int firstPageBox = plan.pageForBox().indexOf(0);
        assertEquals(List.of("a", "a", "a", "a", "b"), plan.targets().get(firstPageBox)
                .slots().stream().map(PackingPlanner.Stack::key).toList());
    }

    private static List<PackingPlanner.Box<String>> execute(
            List<PackingPlanner.Box<String>> input, GlobalSortPlanner.Plan<String> plan) {
        Map<Integer, List<PackingPlanner.Stack<String>>> state = new java.util.TreeMap<>();
        for (PackingPlanner.Box<String> box : input) {
            state.put(box.inventorySlot(), new ArrayList<>(box.slots()));
        }
        for (PackingPlanner.Transfer<String> move : plan.merges()) {
            List<PackingPlanner.Stack<String>> sourceBox = state.get(move.sourceInventorySlot());
            List<PackingPlanner.Stack<String>> targetBox = state.get(move.targetInventorySlot());
            PackingPlanner.Stack<String> source = sourceBox.get(move.sourceBoxSlot());
            PackingPlanner.Stack<String> target = targetBox.get(move.targetBoxSlot());
            int sourceCount = source.count() - move.amount();
            int targetCount = target.count() + move.amount();
            sourceBox.set(move.sourceBoxSlot(), sourceCount == 0 ? null
                    : new PackingPlanner.Stack<>(source.key(), sourceCount, source.maxCount()));
            targetBox.set(move.targetBoxSlot(),
                    new PackingPlanner.Stack<>(target.key(), targetCount, target.maxCount()));
        }
        for (GlobalSortPlanner.SwapCycle<String> cycle : plan.swapCycles()) {
            PackingPlanner.Stack<String> register = null;
            for (GlobalSortPlanner.SwapStep<String> step : cycle.steps()) {
                List<PackingPlanner.Stack<String>> box = state.get(step.slot().inventorySlot());
                PackingPlanner.Stack<String> old = box.set(step.slot().boxSlot(), register);
                register = old;
            }
            assertEquals(null, register);
        }
        List<PackingPlanner.Box<String>> result = new ArrayList<>();
        for (Map.Entry<Integer, List<PackingPlanner.Stack<String>>> entry : state.entrySet()) {
            result.add(new PackingPlanner.Box<>(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static void add(List<PackingPlanner.Stack<String>> stacks, String key, int count) {
        for (int index = 0; index < count; index++) stacks.add(stack(key, 64, 64));
    }

    private static List<PackingPlanner.Box<String>> boxesFromStacks(
            int boxSize, int boxCount, List<PackingPlanner.Stack<String>> stacks) {
        List<PackingPlanner.Box<String>> boxes = new ArrayList<>();
        int cursor = 0;
        for (int box = 0; box < boxCount; box++) {
            List<PackingPlanner.Stack<String>> slots = new ArrayList<>();
            for (int slot = 0; slot < boxSize; slot++) {
                slots.add(cursor < stacks.size() ? stacks.get(cursor++) : null);
            }
            boxes.add(new PackingPlanner.Box<>(box * 9, slots));
        }
        return boxes;
    }

    private static long boxesContaining(List<PackingPlanner.Box<String>> boxes, String key) {
        return boxes.stream().filter(box -> box.slots().stream()
                .anyMatch(stack -> stack != null && stack.key().equals(key))).count();
    }

    private static PackingPlanner.Stack<String> firstNonEmptyPage(
            GlobalSortPlanner.Plan<String> plan, int page) {
        int boxIndex = plan.pageForBox().indexOf(page);
        return plan.targets().get(boxIndex).slots().stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
    }

    private static Map<String, Integer> totals(List<PackingPlanner.Box<String>> boxes) {
        Map<String, Integer> result = new HashMap<>();
        for (PackingPlanner.Box<String> box : boxes) {
            for (PackingPlanner.Stack<String> stack : box.slots()) {
                if (stack != null) result.merge(stack.key(), stack.count(), Integer::sum);
            }
        }
        return result;
    }

    @SafeVarargs
    private static PackingPlanner.Box<String> box(int inventorySlot, PackingPlanner.Stack<String>... stacks) {
        List<PackingPlanner.Stack<String>> slots = new ArrayList<>(java.util.Arrays.asList(stacks));
        return new PackingPlanner.Box<>(inventorySlot, slots);
    }

    private static PackingPlanner.Stack<String> stack(String key, int count, int max) {
        return new PackingPlanner.Stack<>(key, count, max);
    }
}
