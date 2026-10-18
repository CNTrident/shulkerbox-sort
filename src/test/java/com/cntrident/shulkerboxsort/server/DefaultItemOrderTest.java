package com.cntrident.shulkerboxsort.server;

import com.cntrident.shulkerboxsort.ShulkerBoxSort;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DefaultItemOrderTest {
    @BeforeAll static void bootstrap() { ServerInventorySorterTest.bootstrap(); }
    @Test void vanillaCategoriesAreBuiltWithoutClientClasses() {
        assertEquals(0, DefaultItemOrder.current().category(new ItemStack(Items.STONE)));
        assertEquals(3, DefaultItemOrder.current().category(new ItemStack(Items.CHEST)));
        assertEquals(4, DefaultItemOrder.current().category(new ItemStack(Items.OBSERVER)));
        assertEquals(5, DefaultItemOrder.current().category(new ItemStack(Items.IRON_PICKAXE)));
        assertEquals(7, DefaultItemOrder.current().category(new ItemStack(Items.APPLE)));
        assertEquals(10, DefaultItemOrder.current().category(new ItemStack(Items.COMMAND_BLOCK)));
    }
    @Test void categoryTakesPrecedenceOverName() {
        var stone = new ItemStack(Items.STONE); stone.set(DataComponents.CUSTOM_NAME, Component.literal("ZZZ"));
        var apple = new ItemStack(Items.APPLE); apple.set(DataComponents.CUSTOM_NAME, Component.literal("AAA"));
        assertTrue(DefaultItemOrder.current().compareItems(stone, apple) < 0);
    }
    @Test void defaultToolPriorityPrecedesCategoriesAndFollowsUpstreamOrder() {
        var order = DefaultItemOrder.current();
        assertTrue(order.compareItems(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.STONE)) < 0);
        assertTrue(order.compareItems(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_PICKAXE)) < 0);
        assertTrue(order.compareItems(new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.NETHERITE_SWORD)) < 0);
    }
    @Test void categoryNameUsesDisplayNameRatherThanItemId() {
        var stone = new ItemStack(Items.STONE); stone.set(DataComponents.CUSTOM_NAME, Component.literal("AAA"));
        var cobble = new ItemStack(Items.COBBLESTONE); cobble.set(DataComponents.CUSTOM_NAME, Component.literal("ZZZ"));
        assertEquals(DefaultItemOrder.current().category(stone), DefaultItemOrder.current().category(cobble));
        assertTrue(DefaultItemOrder.current().compareItems(stone, cobble) < 0);
    }
    @Test void differentComponentsUseUpstreamHashAndSameComponentsUseCount() {
        var a = new ItemStack(Items.STONE, 10); a.set(DataComponents.CUSTOM_NAME, Component.literal("A"));
        var b = new ItemStack(Items.STONE, 20); b.set(DataComponents.CUSTOM_NAME, Component.literal("B"));
        assertEquals(Integer.signum(Integer.compare(a.getComponents().hashCode(), b.getComponents().hashCode())),
                Integer.signum(DefaultItemOrder.current().compareItems(a, b)));
        assertTrue(DefaultItemOrder.current().compareItems(new ItemStack(Items.STONE, 64), new ItemStack(Items.STONE, 1)) < 0);
    }
    @Test void bundlesAndShulkerBoxesRetainDefaultSpecialOrdering() {
        var order = DefaultItemOrder.current();
        var empty = new ItemStack(Items.BUNDLE);
        var filled = new ItemStack(Items.BUNDLE);
        filled.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(
                new net.minecraft.world.item.ItemStackTemplate(Items.STONE, 8))));
        assertTrue(order.compareItems(new ItemStack(Items.STONE), empty) < 0);
        assertTrue(order.compareItems(empty, filled) < 0);
        var box = new ItemStack(Items.SHULKER_BOX);
        var fullBox = new ItemStack(Items.SHULKER_BOX);
        fullBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE))));
        assertTrue(order.compareItems(filled, box) < 0);
        assertTrue(order.compareItems(box, fullBox) < 0);
    }
    @Test void sortingCommandHasNoOpRequirementOrOtherPlayerArgument() {
        var command = ShulkerBoxSort.command().build();
        // Brigadier's default requirement accepts even a source without a
        // permission context. The command body later requires a real player.
        assertTrue(command.getRequirement().test(null));
        assertEquals("shulkersort", command.getName());
        assertTrue(command.getChildren().isEmpty());
        assertNotNull(command.getCommand());
    }
}
