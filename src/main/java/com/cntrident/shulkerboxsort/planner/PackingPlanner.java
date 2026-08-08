package com.cntrident.shulkerboxsort.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure, deterministic planner for compacting partially-filled fixed-size boxes.
 * It first fills earlier partial stacks from matching later stacks and then
 * moves remaining whole stacks into the earliest holes. Every transfer has an
 * empty destination or a component-identical destination, so execution only
 * needs one temporary inventory slot and never needs a cyclic swap.
 */
public final class PackingPlanner {
    private PackingPlanner() {
    }

    public record Stack<K>(K key, int count, int maxCount) {
        public Stack {
            Objects.requireNonNull(key, "key");
            if (count <= 0 || maxCount <= 0 || count > maxCount) {
                throw new IllegalArgumentException("Invalid stack count " + count + "/" + maxCount);
            }
        }
    }

    public record Box<K>(int inventorySlot, List<Stack<K>> slots) {
        public Box {
            if (inventorySlot < 0) {
                throw new IllegalArgumentException("inventorySlot must be non-negative");
            }
            // Empty positions are represented by null; List.copyOf rejects
            // null elements, so retain an immutable defensive ArrayList copy.
            slots = Collections.unmodifiableList(new ArrayList<>(slots));
        }
    }

    public record Transfer<K>(int sourceInventorySlot, int sourceBoxSlot,
                              int targetInventorySlot, int targetBoxSlot,
                              K key, int amount) {
        public Transfer {
            Objects.requireNonNull(key, "key");
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }
    }

    public record Plan<K>(List<Transfer<K>> transfers, List<Box<K>> result,
                          int emptiedBoxes, int remainingNonEmptyBoxes) {
        public Plan {
            transfers = List.copyOf(transfers);
            result = List.copyOf(result);
        }
    }

    public static <K> Plan<K> plan(List<Box<K>> input) {
        if (input.isEmpty()) {
            return new Plan<>(List.of(), List.of(), 0, 0);
        }

        int slotCount = input.getFirst().slots().size();
        List<Box<K>> orderedInput = new ArrayList<>(input);
        orderedInput.sort(Comparator
                .<Box<K>>comparingDouble(PackingPlanner::fullness)
                .reversed()
                .thenComparingInt(Box::inventorySlot));
        List<MutableBox<K>> boxes = new ArrayList<>(orderedInput.size());
        int initialNonEmpty = 0;

        for (Box<K> box : orderedInput) {
            if (box.slots().size() != slotCount) {
                throw new IllegalArgumentException("All boxes must have the same slot count");
            }
            MutableBox<K> mutable = new MutableBox<>(box);
            boxes.add(mutable);
            if (mutable.isNonEmpty()) {
                initialNonEmpty++;
            }
        }

        List<Transfer<K>> transfers = new ArrayList<>();

        // Drain the sparsest boxes first. A source may only move into a box
        // that ranked fuller than it, so the planner never performs pointless
        // slot compaction within one shulker.
        for (int sourceBoxIndex = boxes.size() - 1; sourceBoxIndex > 0; sourceBoxIndex--) {
            MutableBox<K> sourceBox = boxes.get(sourceBoxIndex);
            for (int sourceSlot = 0; sourceSlot < slotCount; sourceSlot++) {
                MutableStack<K> source = sourceBox.slots.get(sourceSlot);
                if (source == null) {
                    continue;
                }

                // First fill component-identical partial stacks in fuller boxes.
                for (int targetBoxIndex = 0;
                     targetBoxIndex < sourceBoxIndex && source.count > 0;
                     targetBoxIndex++) {
                    MutableBox<K> targetBox = boxes.get(targetBoxIndex);
                    for (int targetSlot = 0;
                         targetSlot < slotCount && source.count > 0;
                         targetSlot++) {
                        MutableStack<K> target = targetBox.slots.get(targetSlot);
                        if (target == null || target.count >= target.maxCount
                                || !Objects.equals(target.key, source.key)) {
                            continue;
                        }
                        int amount = Math.min(target.maxCount - target.count, source.count);
                        transfers.add(new Transfer<>(sourceBox.inventorySlot, sourceSlot,
                                targetBox.inventorySlot, targetSlot, source.key, amount));
                        target.count += amount;
                        source.count -= amount;
                    }
                }

                // Then move the remaining whole stack into the first hole of
                // a fuller box. If no such hole exists, leave it in place.
                if (source.count > 0) {
                    boolean moved = false;
                    for (int targetBoxIndex = 0;
                         targetBoxIndex < sourceBoxIndex && !moved;
                         targetBoxIndex++) {
                        MutableBox<K> targetBox = boxes.get(targetBoxIndex);
                        for (int targetSlot = 0; targetSlot < slotCount; targetSlot++) {
                            if (targetBox.slots.get(targetSlot) == null) {
                                transfers.add(new Transfer<>(sourceBox.inventorySlot, sourceSlot,
                                        targetBox.inventorySlot, targetSlot, source.key, source.count));
                                targetBox.slots.set(targetSlot, source);
                                sourceBox.slots.set(sourceSlot, null);
                                source = null;
                                moved = true;
                                break;
                            }
                        }
                    }
                }

                if (source != null && source.count == 0) {
                    sourceBox.slots.set(sourceSlot, null);
                }
            }
        }

        List<Box<K>> result = new ArrayList<>(boxes.size());
        int finalNonEmpty = 0;
        for (MutableBox<K> box : boxes) {
            List<Stack<K>> slots = new ArrayList<>(slotCount);
            for (MutableStack<K> stack : box.slots) {
                slots.add(stack == null ? null : new Stack<>(stack.key, stack.count, stack.maxCount));
            }
            result.add(new Box<>(box.inventorySlot, Collections.unmodifiableList(slots)));
            if (box.isNonEmpty()) {
                finalNonEmpty++;
            }
        }

        return new Plan<>(transfers, result, initialNonEmpty - finalNonEmpty, finalNonEmpty);
    }

    private static <K> double fullness(Box<K> box) {
        double score = 0.0;
        for (Stack<K> stack : box.slots()) {
            if (stack != null) {
                score += (double) stack.count() / stack.maxCount();
            }
        }
        return score;
    }

    private static final class MutableBox<K> {
        private final int inventorySlot;
        private final List<MutableStack<K>> slots;

        private MutableBox(Box<K> box) {
            this.inventorySlot = box.inventorySlot();
            this.slots = new ArrayList<>(box.slots().size());
            for (Stack<K> stack : box.slots()) {
                this.slots.add(stack == null ? null : new MutableStack<>(stack));
            }
        }

        private boolean isNonEmpty() {
            return slots.stream().anyMatch(Objects::nonNull);
        }
    }

    private static final class MutableStack<K> {
        private final K key;
        private int count;
        private final int maxCount;

        private MutableStack(Stack<K> stack) {
            this.key = stack.key();
            this.count = stack.count();
            this.maxCount = stack.maxCount();
        }
    }
}
