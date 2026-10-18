/*
 * SPDX-License-Identifier: LGPL-3.0-only
 * Default sorting portions adapted from Item Scroller by masa and Sakura-Ryoko.
 * Upstream: sakura-ryoko/itemscroller, tag 26.2-0.32.1,
 * commit 28ee11632d9239193d84ca513aebaa9ae728f64c.
 * Files: InventoryUtils.java, SortingCategory.java and config/Configs.java.
 * Server adaptation Copyright (c) 2026 CNTrident.
 * See THIRD_PARTY_NOTICES.md and META-INF/licenses for license texts.
 */
package com.cntrident.shulkerboxsort.server;

import com.cntrident.shulkerboxsort.inventory.ItemStackKey;
import com.cntrident.shulkerboxsort.mixin.CreativeModeTabAccessor;
import com.cntrident.shulkerboxsort.planner.PackingPlanner;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Fixed CATEGORY_NAME defaults, with no client, configuration or library calls. */
public final class DefaultItemOrder implements Comparator<PackingPlanner.Stack<ItemStackKey>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("shulkerbox_sort");
    private static final List<String> TOP_PRIORITY = List.of(
            "minecraft:diamond_sword", "minecraft:diamond_spear", "minecraft:diamond_pickaxe",
            "minecraft:diamond_axe", "minecraft:diamond_shovel", "minecraft:diamond_hoe",
            "minecraft:netherite_sword", "minecraft:netherite_spear", "minecraft:netherite_pickaxe",
            "minecraft:netherite_axe", "minecraft:netherite_shovel", "minecraft:netherite_hoe");
    private static final List<String> CATEGORIES = List.of(
            "building_blocks", "colored_blocks", "natural_blocks", "functional_blocks",
            "redstone_blocks", "tools_and_utilities", "combat", "food_and_drinks",
            "ingredients", "spawn_eggs", "op_blocks", "other");
    private static volatile DefaultItemOrder current = new DefaultItemOrder(Map.of());
    private final Map<Item, Integer> categories;

    private DefaultItemOrder(Map<Item, Integer> categories) { this.categories = Map.copyOf(categories); }
    public static DefaultItemOrder current() { return current; }
    public static void reset() { current = new DefaultItemOrder(Map.of()); }

    /** Built once when server resources are ready and refreshed on data reload. */
    public static void initialize(HolderLookup.Provider registries, FeatureFlagSet features) {
        Map<Item, Integer> categories = new IdentityHashMap<>();
        var context = new CreativeModeTab.ItemDisplayParameters(features, true, registries);
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            var id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            int index = id == null ? -1 : CATEGORIES.indexOf(id.getPath());
            int category = index < 0 ? CATEGORIES.size() - 1 : index;
            Map<Item, Integer> generated = new IdentityHashMap<>();
            try {
                ((CreativeModeTabAccessor) tab).shulkerboxSort$getDisplayItemsGenerator().accept(context,
                        (stack, visibility) -> {
                            if (!stack.isEmpty() && stack.isItemEnabled(features))
                                generated.putIfAbsent(stack.getItem(), category);
                        });
            } catch (RuntimeException | LinkageError failure) {
                // A failing third-party tab must not prevent inventory sorting.
                LOGGER.warn("Unable to read creative category {}; uncategorized items use name order", id, failure);
            }
            generated.forEach(categories::putIfAbsent);
        }
        current = new DefaultItemOrder(categories);
    }

    public int category(ItemStack stack) {
        return categories.getOrDefault(stack.getItem(), CATEGORIES.size() - 1);
    }
    @Override public int compare(PackingPlanner.Stack<ItemStackKey> left,
                                 PackingPlanner.Stack<ItemStackKey> right) {
        return compareItems(left == null ? ItemStack.EMPTY : left.key().template().copyWithCount(left.count()),
                right == null ? ItemStack.EMPTY : right.key().template().copyWithCount(right.count()));
    }
    public int compareItems(ItemStack left, ItemStack right) {
        ItemStack a = left == null ? ItemStack.EMPTY : left;
        ItemStack b = right == null ? ItemStack.EMPTY : right;
        boolean boxA = isBox(a), boxB = isBox(b);
        if (boxA != boxB) return Boolean.compare(boxA, boxB);
        boolean bundleA = isBundle(a), bundleB = isBundle(b);
        if (bundleA != bundleB) return Boolean.compare(bundleA, bundleB);
        int priorityA = priority(a), priorityB = priority(b);
        if (priorityA != -1 || priorityB != -1) return Integer.compare(priorityA, priorityB);
        if (a.isEmpty() != b.isEmpty()) return Boolean.compare(a.isEmpty(), b.isEmpty());
        if (a.isEmpty()) return 0;
        if (boxA && boxB) return Long.compare(
                a.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count(),
                b.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count());
        if (bundleA && bundleB) return a.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                .weight().getOrThrow().compareTo(b.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                        .weight().getOrThrow());
        int categoryResult = Integer.compare(category(a), category(b));
        if (categoryResult != 0) return categoryResult;
        if (a.getItem() != b.getItem()) return a.getHoverName().getString().compareTo(b.getHoverName().getString());
        if (!ItemStack.isSameItemSameComponents(a, b))
            return Integer.compare(a.getComponents().hashCode(), b.getComponents().hashCode());
        return Integer.compare(b.getCount(), a.getCount());
    }
    private static int priority(ItemStack stack) {
        if (stack.isEmpty()) return -1;
        int name = TOP_PRIORITY.indexOf(stack.getHoverName().getString());
        int id = TOP_PRIORITY.indexOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        int index = name >= 0 ? name : id;
        return index < 0 ? -1 : index - TOP_PRIORITY.size() - 2;
    }
    private static boolean isBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock;
    }
    private static boolean isBundle(ItemStack stack) {
        return stack.is(Items.BUNDLE) || stack.has(DataComponents.BUNDLE_CONTENTS);
    }
}
