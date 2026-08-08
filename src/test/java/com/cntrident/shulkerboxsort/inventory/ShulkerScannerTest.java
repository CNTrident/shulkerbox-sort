package com.cntrident.shulkerboxsort.inventory;

import com.cntrident.shulkerboxsort.planner.PackingPlanner;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShulkerScannerTest {
    @Test
    void locksOnlyCapacityFullHomogeneousLayouts() {
        List<PackingPlanner.Stack<String>> homogeneous = filled("redstone", 27, 64, 64);
        assertTrue(ShulkerScanner.isHomogeneousCapacityFull(homogeneous));

        List<PackingPlanner.Stack<String>> mixed = new ArrayList<>(homogeneous);
        mixed.set(26, new PackingPlanner.Stack<>("observer", 64, 64));
        assertFalse(ShulkerScanner.isHomogeneousCapacityFull(mixed));

        List<PackingPlanner.Stack<String>> partial = new ArrayList<>(homogeneous);
        partial.set(26, new PackingPlanner.Stack<>("redstone", 63, 64));
        assertFalse(ShulkerScanner.isHomogeneousCapacityFull(partial));
    }

    private static List<PackingPlanner.Stack<String>> filled(
            String key, int size, int count, int maxCount) {
        List<PackingPlanner.Stack<String>> result = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            result.add(new PackingPlanner.Stack<>(key, count, maxCount));
        }
        return result;
    }
}
