package com.cntrident.shulkerboxsort.mixin;

import com.cntrident.shulkerboxsort.session.SortSessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void shulkerboxSort$blockInventoryDuringSort(Screen screen, CallbackInfo ci) {
        if (screen instanceof InventoryScreen && SortSessionManager.INSTANCE.isRunning()) {
            Minecraft client = (Minecraft) (Object) this;
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.translatable(
                        "message.shulkerbox_sort.inventory_disabled"));
            }
            // Keep the synchronized hidden Quick Shulker menu intact. Only
            // InventoryScreen is blocked; perspective and other key handling
            // remain available.
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void shulkerboxSort$hideExpectedContainerScreen(Screen screen, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (screen != null && SortSessionManager.INSTANCE.consumeExpectedContainerScreen(screen)) {
            // The real server-backed menu has already been installed. Remove
            // only its visual screen and return input to normal gameplay.
            client.screen = null;
            client.mouseHandler.grabMouse();
        }
    }
}
