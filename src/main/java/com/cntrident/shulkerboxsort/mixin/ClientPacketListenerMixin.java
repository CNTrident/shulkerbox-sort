package com.cntrident.shulkerboxsort.mixin;

import com.cntrident.shulkerboxsort.network.PacketSyncTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void shulkerboxSort$onContainerContent(ClientboundContainerSetContentPacket packet,
                                                    CallbackInfo callbackInfo) {
        PacketSyncTracker.recordContent(packet.containerId(), packet.stateId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void shulkerboxSort$onContainerSlot(ClientboundContainerSetSlotPacket packet,
                                                 CallbackInfo callbackInfo) {
        PacketSyncTracker.recordSlot(packet.getContainerId(), packet.getStateId());
    }
}
