package com.cntrident.shulkerboxsort.server;

import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Field;

/** Respect actual stack components and Carpet's independent empty-shulker rule. */
public final class EmptyBoxStackLimit {
    private static final Field CARPET_LIMIT = findCarpetLimit();
    private EmptyBoxStackLimit() {}
    public static int get(ItemStack stack) {
        int limit = stack.getMaxStackSize();
        if (CARPET_LIMIT != null) {
            try { limit = Math.max(limit, CARPET_LIMIT.getInt(null)); }
            catch (ReflectiveOperationException | RuntimeException ignored) { /* component limit remains */ }
        }
        return Math.max(1, Math.min(99, limit));
    }
    private static Field findCarpetLimit() {
        try { return Class.forName("carpet.CarpetSettings").getField("shulkerBoxStackSize"); }
        catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) { return null; }
    }
}
