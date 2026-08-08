package com.cntrident.shulkerboxsort.inventory;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Exact item + data-component identity, deliberately ignoring only count. */
public final class ItemStackKey {
    private final ItemStack template;
    private final int hash;

    public ItemStackKey(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("An empty stack has no identity");
        }
        this.template = stack.copyWithCount(1);
        this.hash = ItemStack.hashItemAndComponents(this.template);
    }

    public ItemStack template() {
        return template.copy();
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItemSameComponents(template, stack);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ItemStackKey key
                && ItemStack.isSameItemSameComponents(template, key.template);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return Objects.toString(template);
    }
}
