package com.cntrident.shulkerboxsort.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure planner for merging, globally sorting and redistributing fixed-size boxes.
 * Empty positions are represented by {@code null} stacks.
 */
public final class GlobalSortPlanner {
    private static final int BULK_STACK_THRESHOLD = 6;
    private static final long PRIMARY_SCALE = 1_000_000_000L;
    private static final long SECONDARY_SCALE = 2_000L;

    private GlobalSortPlanner() {
    }

    public record SlotRef(int inventorySlot, int boxSlot) {
    }

    public record SwapStep<K>(SlotRef slot,
                              PackingPlanner.Stack<K> slotBefore,
                              PackingPlanner.Stack<K> registerBefore,
                              PackingPlanner.Stack<K> slotAfter,
                              PackingPlanner.Stack<K> registerAfter) {
    }

    public record SwapCycle<K>(List<SwapStep<K>> steps) {
        public SwapCycle {
            steps = List.copyOf(steps);
            if (steps.isEmpty()
                    || steps.getFirst().registerBefore() != null
                    || steps.getLast().registerAfter() != null) {
                throw new IllegalArgumentException("A swap cycle must start and end with an empty register");
            }
        }
    }

    public record Plan<K>(List<PackingPlanner.Transfer<K>> merges,
                          List<PackingPlanner.Box<K>> afterMerges,
                          List<PackingPlanner.Box<K>> targets,
                          List<SwapCycle<K>> swapCycles,
                          List<Integer> pageForBox,
                          int emptiedBoxes,
                          int remainingNonEmptyBoxes,
                          int crossBoxMoves) {
        public Plan {
            merges = List.copyOf(merges);
            afterMerges = List.copyOf(afterMerges);
            targets = List.copyOf(targets);
            swapCycles = List.copyOf(swapCycles);
            pageForBox = List.copyOf(pageForBox);
        }
    }

    public static <K> Plan<K> plan(List<PackingPlanner.Box<K>> input,
                                   Comparator<PackingPlanner.Stack<K>> stackComparator) {
        Objects.requireNonNull(stackComparator, "stackComparator");
        if (input.isEmpty()) {
            return new Plan<>(List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0, 0);
        }

        List<PackingPlanner.Box<K>> boxes = new ArrayList<>(input);
        boxes.sort(Comparator.comparingInt(PackingPlanner.Box::inventorySlot));
        int boxSize = boxes.getFirst().slots().size();
        for (PackingPlanner.Box<K> box : boxes) {
            if (box.slots().size() != boxSize) {
                throw new IllegalArgumentException("All boxes must have the same slot count");
            }
        }

        MergeResult<K> mergeResult = mergeToCanonicalStacks(boxes, boxSize);
        // Item Scroller may use stack count as a tie breaker. Count is not part
        // of the requested global item order: reordering component-identical
        // stacks only to put a partial stack last creates expensive "bubbling"
        // without saving a slot. Keep their physical first-occurrence order.
        Comparator<PackingPlanner.Stack<K>> itemOrder = (left, right) ->
                Objects.equals(left.key(), right.key()) ? 0 : stackComparator.compare(left, right);
        List<PackingPlanner.Stack<K>> sorted = new ArrayList<>();
        for (PackingPlanner.Box<K> box : mergeResult.boxes) {
            for (PackingPlanner.Stack<K> stack : box.slots()) {
                if (stack != null) {
                    sorted.add(stack);
                }
            }
        }
        sorted.sort(itemOrder); // TimSort is stable, preserving first occurrence on ties.

        List<List<PackingPlanner.Stack<K>>> pages = partitionPages(
                sorted, boxes.size(), boxSize, itemOrder);

        int[] pageForBox = maximumWeightAssignment(boxes, pages);
        List<PackingPlanner.Box<K>> targets = new ArrayList<>(boxes.size());
        List<Integer> pageMapping = new ArrayList<>(boxes.size());
        for (int boxIndex = 0; boxIndex < boxes.size(); boxIndex++) {
            targets.add(new PackingPlanner.Box<>(boxes.get(boxIndex).inventorySlot(), pages.get(pageForBox[boxIndex])));
            pageMapping.add(pageForBox[boxIndex]);
        }

        List<SwapCycle<K>> cycles = buildSwapCycles(mergeResult.boxes, targets, boxSize);
        int initialNonEmpty = countNonEmptyBoxes(boxes);
        int finalNonEmpty = countNonEmptyBoxes(targets);
        int crossBoxMoves = mergeResult.merges.stream()
                .mapToInt(move -> move.sourceInventorySlot() == move.targetInventorySlot() ? 0 : 1)
                .sum();
        for (SwapCycle<K> cycle : cycles) {
            SlotRef registerSource = null;
            for (SwapStep<K> step : cycle.steps()) {
                if (step.registerBefore() != null && registerSource != null
                        && registerSource.inventorySlot() != step.slot().inventorySlot()) {
                    crossBoxMoves++;
                }
                registerSource = step.slotBefore() == null ? null : step.slot();
            }
        }
        return new Plan<>(mergeResult.merges, mergeResult.boxes, targets, cycles, pageMapping,
                initialNonEmpty - finalNonEmpty, finalNonEmpty, crossBoxMoves);
    }

    /**
     * Keeps six-or-more-stack item groups together where the minimum possible
     * number of non-empty pages allows it. Large groups are allocated first;
     * small groups retain Item Scroller order and fill the remaining holes.
     */
    private static <K> List<List<PackingPlanner.Stack<K>>> partitionPages(
            List<PackingPlanner.Stack<K>> sorted, int boxCount, int boxSize,
            Comparator<PackingPlanner.Stack<K>> stackComparator) {
        int requiredPages = (sorted.size() + boxSize - 1) / boxSize;
        LinkedHashMap<K, List<PackingPlanner.Stack<K>>> byKey = new LinkedHashMap<>();
        Map<K, Integer> firstIndex = new HashMap<>();
        for (int index = 0; index < sorted.size(); index++) {
            PackingPlanner.Stack<K> stack = sorted.get(index);
            byKey.computeIfAbsent(stack.key(), ignored -> new ArrayList<>()).add(stack);
            firstIndex.putIfAbsent(stack.key(), index);
        }

        List<K> bulkKeys = byKey.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= BULK_STACK_THRESHOLD)
                .sorted(Comparator.<Map.Entry<K, List<PackingPlanner.Stack<K>>>>
                                comparingInt(entry -> entry.getValue().size()).reversed()
                        .thenComparingInt(entry -> firstIndex.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .toList();
        if (bulkKeys.isEmpty()) {
            return sequentialPages(sorted, boxCount, boxSize);
        }

        List<List<PackingPlanner.Stack<K>>> usedPages = new ArrayList<>(requiredPages);
        for (K key : bulkKeys) {
            List<PackingPlanner.Stack<K>> group = new ArrayList<>(byKey.get(key));
            // A key spanning more than one page must create its homogeneous
            // full page from full stacks. Its sole remainder is kept outside
            // that page, avoiding a slot-by-slot shift through all 27 slots.
            if (group.size() > boxSize) {
                group.sort(Comparator.comparingInt(stack ->
                        stack.count() == stack.maxCount() ? 0 : 1));
            }
            int cursor = 0;
            while (group.size() - cursor >= boxSize) {
                List<PackingPlanner.Stack<K>> page = new ArrayList<>(boxSize);
                page.addAll(group.subList(cursor, cursor + boxSize));
                usedPages.add(page);
                cursor += boxSize;
            }
            int remaining = group.size() - cursor;
            if (remaining > 0) {
                List<PackingPlanner.Stack<K>> page = bestFittingPage(usedPages, boxSize, remaining);
                if (page == null && usedPages.size() < requiredPages) {
                    page = new ArrayList<>(boxSize);
                    usedPages.add(page);
                }
                while (remaining > 0) {
                    if (page == null) {
                        page = pageWithMostSpace(usedPages, boxSize);
                    }
                    int amount = Math.min(remaining, boxSize - page.size());
                    page.addAll(group.subList(cursor, cursor + amount));
                    cursor += amount;
                    remaining -= amount;
                    page = remaining == 0 ? null : pageWithMostSpace(usedPages, boxSize);
                }
            }
        }

        List<PackingPlanner.Stack<K>> small = new ArrayList<>();
        for (PackingPlanner.Stack<K> stack : sorted) {
            if (byKey.get(stack.key()).size() < BULK_STACK_THRESHOLD) {
                small.add(stack);
            }
        }
        int smallCursor = 0;
        for (List<PackingPlanner.Stack<K>> page : usedPages) {
            while (page.size() < boxSize && smallCursor < small.size()) {
                page.add(small.get(smallCursor++));
            }
        }
        while (smallCursor < small.size()) {
            List<PackingPlanner.Stack<K>> page = new ArrayList<>(boxSize);
            while (page.size() < boxSize && smallCursor < small.size()) {
                page.add(small.get(smallCursor++));
            }
            usedPages.add(page);
        }
        if (usedPages.size() > requiredPages) {
            throw new IllegalStateException("Bulk pagination exceeded the minimum page count");
        }

        List<List<PackingPlanner.Stack<K>>> result = new ArrayList<>(boxCount);
        for (List<PackingPlanner.Stack<K>> page : usedPages) {
            page.sort(stackComparator);
            while (page.size() < boxSize) page.add(null);
            result.add(Collections.unmodifiableList(new ArrayList<>(page)));
        }
        while (result.size() < boxCount) {
            result.add(Collections.unmodifiableList(new ArrayList<>(
                    Collections.nCopies(boxSize, null))));
        }
        return result;
    }

    private static <K> List<List<PackingPlanner.Stack<K>>> sequentialPages(
            List<PackingPlanner.Stack<K>> sorted, int boxCount, int boxSize) {
        List<PackingPlanner.Stack<K>> padded = new ArrayList<>(sorted);
        while (padded.size() < boxCount * boxSize) padded.add(null);
        List<List<PackingPlanner.Stack<K>>> result = new ArrayList<>(boxCount);
        for (int page = 0; page < boxCount; page++) {
            result.add(Collections.unmodifiableList(new ArrayList<>(
                    padded.subList(page * boxSize, (page + 1) * boxSize))));
        }
        return result;
    }

    private static <K> List<PackingPlanner.Stack<K>> bestFittingPage(
            List<List<PackingPlanner.Stack<K>>> pages, int boxSize, int requiredSpace) {
        List<PackingPlanner.Stack<K>> best = null;
        int bestRemaining = Integer.MAX_VALUE;
        for (List<PackingPlanner.Stack<K>> page : pages) {
            int free = boxSize - page.size();
            if (free >= requiredSpace && free - requiredSpace < bestRemaining) {
                best = page;
                bestRemaining = free - requiredSpace;
            }
        }
        return best;
    }

    private static <K> List<PackingPlanner.Stack<K>> pageWithMostSpace(
            List<List<PackingPlanner.Stack<K>>> pages, int boxSize) {
        List<PackingPlanner.Stack<K>> best = null;
        int mostSpace = 0;
        for (List<PackingPlanner.Stack<K>> page : pages) {
            int free = boxSize - page.size();
            if (free > mostSpace) {
                best = page;
                mostSpace = free;
            }
        }
        if (best == null) {
            throw new IllegalStateException("No room remains for a bulk stack group");
        }
        return best;
    }

    private static <K> MergeResult<K> mergeToCanonicalStacks(List<PackingPlanner.Box<K>> input, int boxSize) {
        List<MutableBox<K>> boxes = mutableCopy(input);
        List<Position> positions = allPositions(boxes.size(), boxSize);
        List<PackingPlanner.Transfer<K>> merges = new ArrayList<>();

        LinkedHashMap<K, List<Position>> byKey = new LinkedHashMap<>();
        for (Position position : positions) {
            MutableStack<K> stack = boxes.get(position.box).slots.get(position.slot);
            if (stack != null) {
                byKey.computeIfAbsent(stack.key, ignored -> new ArrayList<>()).add(position);
            }
        }

        for (List<Position> keyPositions : byKey.values()) {
            long total = 0;
            int maxCount = 1;
            for (Position position : keyPositions) {
                MutableStack<K> stack = boxes.get(position.box).slots.get(position.slot);
                if (stack != null) {
                    total += stack.count;
                    maxCount = stack.maxCount;
                }
            }
            int canonicalStackCount = (int) ((total + maxCount - 1) / maxCount);
            // If consolidation cannot reduce the occupied slot count and the
            // item fits in one box, leave partial stacks exactly where they
            // first appeared. There is no capacity benefit in normalizing
            // [partial, full] to [full, partial].
            if (canonicalStackCount == keyPositions.size()
                    && canonicalStackCount <= boxSize) {
                continue;
            }
            for (int targetIndex = 0; targetIndex < keyPositions.size(); targetIndex++) {
                Position targetPosition = keyPositions.get(targetIndex);
                MutableStack<K> target = boxes.get(targetPosition.box).slots.get(targetPosition.slot);
                if (target == null) {
                    continue;
                }
                // Pull from the tail directly. Pulling from the immediately
                // following slot turns one early partial stack into 27
                // successive moves (the partial "bubbles" to the end).
                for (int sourceIndex = keyPositions.size() - 1;
                     sourceIndex > targetIndex && target.count < target.maxCount;
                     sourceIndex--) {
                    Position sourcePosition = keyPositions.get(sourceIndex);
                    MutableStack<K> source = boxes.get(sourcePosition.box).slots.get(sourcePosition.slot);
                    if (source == null) {
                        continue;
                    }
                    int amount = Math.min(target.maxCount - target.count, source.count);
                    merges.add(new PackingPlanner.Transfer<>(
                            boxes.get(sourcePosition.box).inventorySlot, sourcePosition.slot,
                            boxes.get(targetPosition.box).inventorySlot, targetPosition.slot,
                            source.key, amount));
                    target.count += amount;
                    source.count -= amount;
                    if (source.count == 0) {
                        boxes.get(sourcePosition.box).slots.set(sourcePosition.slot, null);
                    }
                }
            }
        }
        return new MergeResult<>(List.copyOf(merges), immutableCopy(boxes));
    }

    private static <K> int[] maximumWeightAssignment(List<PackingPlanner.Box<K>> boxes,
                                                       List<List<PackingPlanner.Stack<K>>> pages) {
        int n = boxes.size();
        long[][] weight = new long[n][n];
        for (int box = 0; box < n; box++) {
            Map<K, Integer> boxTotals = totals(boxes.get(box).slots());
            for (int page = 0; page < n; page++) {
                Map<K, Integer> pageTotals = totals(pages.get(page));
                long overlap = 0;
                for (Map.Entry<K, Integer> entry : boxTotals.entrySet()) {
                    overlap += Math.min(entry.getValue(), pageTotals.getOrDefault(entry.getKey(), 0));
                }
                long exact = 0;
                for (int slot = 0; slot < boxes.get(box).slots().size(); slot++) {
                    PackingPlanner.Stack<K> left = boxes.get(box).slots().get(slot);
                    PackingPlanner.Stack<K> right = pages.get(page).get(slot);
                    if (sameStack(left, right)) {
                        exact += left == null ? 0 : left.count();
                    }
                }
                long deterministic = n - Math.abs(box - page);
                weight[box][page] = overlap * PRIMARY_SCALE + exact * SECONDARY_SCALE + deterministic;
            }
        }
        return hungarianMaximum(weight);
    }

    /** Hungarian assignment for a square maximum-weight matrix. */
    private static int[] hungarianMaximum(long[][] weight) {
        int n = weight.length;
        long max = 0;
        for (long[] row : weight) {
            for (long value : row) {
                max = Math.max(max, value);
            }
        }
        long[] u = new long[n + 1];
        long[] v = new long[n + 1];
        int[] p = new int[n + 1];
        int[] way = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            long[] minv = new long[n + 1];
            boolean[] used = new boolean[n + 1];
            java.util.Arrays.fill(minv, Long.MAX_VALUE / 4);
            int j0 = 0;
            do {
                used[j0] = true;
                int i0 = p[j0];
                long delta = Long.MAX_VALUE / 4;
                int j1 = 0;
                for (int j = 1; j <= n; j++) {
                    if (used[j]) continue;
                    long cur = (max - weight[i0 - 1][j - 1]) - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                for (int j = 0; j <= n; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] assignment = new int[n];
        for (int j = 1; j <= n; j++) {
            assignment[p[j] - 1] = j - 1;
        }
        return assignment;
    }

    private static <K> List<SwapCycle<K>> buildSwapCycles(List<PackingPlanner.Box<K>> current,
                                                           List<PackingPlanner.Box<K>> targets,
                                                           int boxSize) {
        List<SlotRef> refs = new ArrayList<>();
        List<PackingPlanner.Stack<K>> source = new ArrayList<>();
        List<PackingPlanner.Stack<K>> target = new ArrayList<>();
        for (int box = 0; box < current.size(); box++) {
            for (int slot = 0; slot < boxSize; slot++) {
                refs.add(new SlotRef(current.get(box).inventorySlot(), slot));
                source.add(current.get(box).slots().get(slot));
                target.add(targets.get(box).slots().get(slot));
            }
        }

        int size = refs.size();
        int[] destination = new int[size];
        java.util.Arrays.fill(destination, -1);
        boolean[] targetUsed = new boolean[size];
        for (int i = 0; i < size; i++) {
            if (sameStack(source.get(i), target.get(i))) {
                destination[i] = i;
                targetUsed[i] = true;
            }
        }
        for (int sourceIndex = 0; sourceIndex < size; sourceIndex++) {
            if (destination[sourceIndex] >= 0) continue;
            int best = -1;
            for (int targetIndex = 0; targetIndex < size; targetIndex++) {
                if (targetUsed[targetIndex] || !sameStack(source.get(sourceIndex), target.get(targetIndex))) {
                    continue;
                }
                if (best < 0 || sameBox(refs.get(sourceIndex), refs.get(targetIndex))
                        && !sameBox(refs.get(sourceIndex), refs.get(best))) {
                    best = targetIndex;
                }
            }
            if (best < 0) {
                throw new IllegalStateException("Source and target stack multisets differ");
            }
            destination[sourceIndex] = best;
            targetUsed[best] = true;
        }

        boolean[] visited = new boolean[size];
        List<SwapCycle<K>> cycles = new ArrayList<>();
        for (int seed = 0; seed < size; seed++) {
            if (visited[seed] || destination[seed] == seed) continue;
            List<Integer> cycle = new ArrayList<>();
            int cursor = seed;
            do {
                cycle.add(cursor);
                visited[cursor] = true;
                cursor = destination[cursor];
            } while (cursor != seed);

            int emptyIndex = -1;
            for (int i = 0; i < cycle.size(); i++) {
                if (source.get(cycle.get(i)) == null) {
                    emptyIndex = i;
                    break;
                }
            }
            List<Integer> swapPositions = new ArrayList<>();
            if (emptyIndex >= 0) {
                for (int offset = 1; offset < cycle.size(); offset++) {
                    swapPositions.add(cycle.get((emptyIndex + offset) % cycle.size()));
                }
                swapPositions.add(cycle.get(emptyIndex));
            } else {
                swapPositions.addAll(cycle);
                swapPositions.add(cycle.getFirst());
            }

            PackingPlanner.Stack<K> register = null;
            List<PackingPlanner.Stack<K>> simulation = new ArrayList<>(source);
            List<SwapStep<K>> steps = new ArrayList<>();
            for (int position : swapPositions) {
                PackingPlanner.Stack<K> slotBefore = simulation.get(position);
                PackingPlanner.Stack<K> registerBefore = register;
                simulation.set(position, register);
                register = slotBefore;
                steps.add(new SwapStep<>(refs.get(position), slotBefore, registerBefore,
                        simulation.get(position), register));
            }
            if (register != null) {
                throw new IllegalStateException("Cycle did not clear the swap register");
            }
            cycles.add(new SwapCycle<>(steps));
        }
        return cycles;
    }

    private static boolean sameBox(SlotRef left, SlotRef right) {
        return left.inventorySlot() == right.inventorySlot();
    }

    private static <K> boolean sameStack(PackingPlanner.Stack<K> left, PackingPlanner.Stack<K> right) {
        return left == right || left != null && right != null
                && left.count() == right.count()
                && left.maxCount() == right.maxCount()
                && Objects.equals(left.key(), right.key());
    }

    private static <K> Map<K, Integer> totals(List<PackingPlanner.Stack<K>> stacks) {
        Map<K, Integer> result = new HashMap<>();
        for (PackingPlanner.Stack<K> stack : stacks) {
            if (stack != null) result.merge(stack.key(), stack.count(), Integer::sum);
        }
        return result;
    }

    private static <K> int countNonEmptyBoxes(List<PackingPlanner.Box<K>> boxes) {
        return (int) boxes.stream().filter(box -> box.slots().stream().anyMatch(Objects::nonNull)).count();
    }

    private static List<Position> allPositions(int boxCount, int boxSize) {
        List<Position> result = new ArrayList<>(boxCount * boxSize);
        for (int box = 0; box < boxCount; box++) {
            for (int slot = 0; slot < boxSize; slot++) result.add(new Position(box, slot));
        }
        return result;
    }

    private static <K> List<MutableBox<K>> mutableCopy(List<PackingPlanner.Box<K>> boxes) {
        List<MutableBox<K>> result = new ArrayList<>();
        for (PackingPlanner.Box<K> box : boxes) result.add(new MutableBox<>(box));
        return result;
    }

    private static <K> List<PackingPlanner.Box<K>> immutableCopy(List<MutableBox<K>> boxes) {
        List<PackingPlanner.Box<K>> result = new ArrayList<>();
        for (MutableBox<K> box : boxes) {
            List<PackingPlanner.Stack<K>> slots = new ArrayList<>();
            for (MutableStack<K> stack : box.slots) {
                slots.add(stack == null ? null : new PackingPlanner.Stack<>(stack.key, stack.count, stack.maxCount));
            }
            result.add(new PackingPlanner.Box<>(box.inventorySlot, slots));
        }
        return List.copyOf(result);
    }

    private record Position(int box, int slot) {
    }

    private record MergeResult<K>(List<PackingPlanner.Transfer<K>> merges,
                                  List<PackingPlanner.Box<K>> boxes) {
    }

    private static final class MutableBox<K> {
        private final int inventorySlot;
        private final List<MutableStack<K>> slots = new ArrayList<>();

        private MutableBox(PackingPlanner.Box<K> box) {
            inventorySlot = box.inventorySlot();
            for (PackingPlanner.Stack<K> stack : box.slots()) {
                slots.add(stack == null ? null : new MutableStack<>(stack));
            }
        }
    }

    private static final class MutableStack<K> {
        private final K key;
        private int count;
        private final int maxCount;

        private MutableStack(PackingPlanner.Stack<K> stack) {
            key = stack.key();
            count = stack.count();
            maxCount = stack.maxCount();
        }
    }
}
