package com.cntrident.shulkerboxsort.planner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackingPlannerTest {
    @Test
    void fillsTheFullerBoxAndEmptiesTheSparserBox() {
        var plan = PackingPlanner.plan(List.of(
                box(9, 3, stack("stone", 40, 64), null, null),
                box(10, 3, stack("stone", 30, 64), stack("dirt", 64, 64), null)
        ));

        assertEquals(2, plan.transfers().size());
        assertEquals(new PackingPlanner.Transfer<>(9, 0, 10, 0, "stone", 34), plan.transfers().get(0));
        assertEquals(new PackingPlanner.Transfer<>(9, 0, 10, 2, "stone", 6), plan.transfers().get(1));
        assertEquals(1, plan.emptiedBoxes());
        assertEquals(1, plan.remainingNonEmptyBoxes());
        assertEquals(64, plan.result().get(0).slots().get(0).count());
        assertEquals(64, plan.result().get(0).slots().get(1).count());
        assertEquals(6, plan.result().get(0).slots().get(2).count());
        assertEquals(10, plan.result().get(0).inventorySlot());
    }

    @Test
    void doesNotMergeDifferentComponentKeys() {
        var plan = PackingPlanner.plan(List.of(
                box(9, 2, stack("potion:healing", 1, 1), null),
                box(10, 2, stack("potion:strength", 1, 1), null)
        ));

        assertTrue(plan.transfers().stream().noneMatch(t -> t.targetBoxSlot() == 0));
        assertEquals("potion:strength", plan.result().get(0).slots().get(1).key());
        assertEquals(1, plan.emptiedBoxes());
    }

    @Test
    void handlesSixteenAndOneSizedStacksDeterministically() {
        var plan = PackingPlanner.plan(List.of(
                box(3, 3, stack("pearl", 9, 16), stack("sword", 1, 1), null),
                box(8, 3, stack("pearl", 12, 16), null, null)
        ));

        assertEquals(new PackingPlanner.Transfer<>(8, 0, 3, 0, "pearl", 7), plan.transfers().get(0));
        assertEquals(new PackingPlanner.Transfer<>(8, 0, 3, 2, "pearl", 5), plan.transfers().get(1));
        assertEquals(1, plan.emptiedBoxes());
    }

    @Test
    void emptyInputIsStable() {
        var plan = PackingPlanner.<String>plan(List.of());
        assertTrue(plan.transfers().isEmpty());
        assertEquals(0, plan.emptiedBoxes());
    }

    @Test
    void packsIntoTheFewestBoxesAndPreservesEveryItem() {
        List<PackingPlanner.Box<String>> input = List.of(
                box(2, 2, stack("a", 32, 64), stack("b", 1, 1)),
                box(7, 2, stack("a", 32, 64), stack("c", 8, 16)),
                box(12, 2, stack("c", 8, 16), null)
        );

        var plan = PackingPlanner.plan(input);

        assertEquals(2, plan.remainingNonEmptyBoxes());
        assertEquals(1, plan.emptiedBoxes());
        assertEquals(totals(input), totals(plan.result()));
        assertTrue(plan.result().get(2).slots().stream().allMatch(value -> value == null));
    }

    @Test
    void singleItemsNeverMergeButStillCompactInFirstOccurrenceOrder() {
        var plan = PackingPlanner.plan(List.of(
                box(1, 2, stack("named-tool-a", 1, 1), null),
                box(5, 2, stack("named-tool-b", 1, 1), null)
        ));

        assertEquals("named-tool-a", plan.result().get(0).slots().get(0).key());
        assertEquals("named-tool-b", plan.result().get(0).slots().get(1).key());
        assertEquals(1, plan.emptiedBoxes());
    }

    @Test
    void repeatedPlanningIsDeterministic() {
        List<PackingPlanner.Box<String>> input = List.of(
                box(4, 3, stack("x", 7, 16), null, stack("y", 3, 64)),
                box(11, 3, stack("x", 12, 16), stack("y", 61, 64), null)
        );

        assertEquals(PackingPlanner.plan(input), PackingPlanner.plan(input));
    }

    @Test
    void leavesAtMostOnePartialBoxAndEmptiesAllLaterBoxes() {
        var plan = PackingPlanner.plan(List.of(
                box(0, 3, stack("a", 1, 1), null, null),
                box(9, 3, stack("b", 1, 1), stack("c", 1, 1), null),
                box(18, 3, stack("d", 1, 1), stack("e", 1, 1), null),
                box(27, 3, stack("f", 1, 1), stack("g", 1, 1), null)
        ));

        assertTrue(plan.result().get(0).slots().stream().allMatch(value -> value != null));
        assertTrue(plan.result().get(1).slots().stream().allMatch(value -> value != null));
        assertEquals(1, plan.result().get(2).slots().stream().filter(value -> value != null).count());
        assertTrue(plan.result().get(3).slots().stream().allMatch(value -> value == null));
        assertEquals(1, plan.emptiedBoxes());
    }

    @Test
    void neverCompactsGapsInsideTheSameBox() {
        var plan = PackingPlanner.plan(List.of(
                box(9, 5, null, stack("a", 16, 64), null, stack("b", 1, 1), null)
        ));

        assertTrue(plan.transfers().isEmpty());
        assertEquals("a", plan.result().get(0).slots().get(1).key());
        assertEquals("b", plan.result().get(0).slots().get(3).key());
    }

    @Test
    void everyMoveCrossesBetweenDifferentBoxes() {
        var plan = PackingPlanner.plan(List.of(
                box(4, 3, stack("a", 64, 64), null, null),
                box(12, 3, null, stack("b", 32, 64), stack("c", 1, 1))
        ));

        assertTrue(plan.transfers().stream().allMatch(move ->
                move.sourceInventorySlot() != move.targetInventorySlot()));
    }

    private static Map<String, Integer> totals(List<PackingPlanner.Box<String>> boxes) {
        Map<String, Integer> totals = new HashMap<>();
        for (PackingPlanner.Box<String> box : boxes) {
            for (PackingPlanner.Stack<String> stack : box.slots()) {
                if (stack != null) {
                    totals.merge(stack.key(), stack.count(), Integer::sum);
                }
            }
        }
        return totals;
    }

    private static PackingPlanner.Stack<String> stack(String key, int count, int max) {
        return new PackingPlanner.Stack<>(key, count, max);
    }

    @SafeVarargs
    private static PackingPlanner.Box<String> box(int inventorySlot, int size,
                                                   PackingPlanner.Stack<String>... values) {
        List<PackingPlanner.Stack<String>> slots = new ArrayList<>();
        for (PackingPlanner.Stack<String> value : values) {
            slots.add(value);
        }
        while (slots.size() < size) {
            slots.add(null);
        }
        return new PackingPlanner.Box<>(inventorySlot, slots);
    }
}
