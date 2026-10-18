package com.cntrident.shulkerboxsort.mixin;

import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Evaluate categories without modifying the client's shared creative-tab cache. */
@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
    @Accessor("displayItemsGenerator")
    CreativeModeTab.DisplayItemsGenerator shulkerboxSort$getDisplayItemsGenerator();
}
