package com.cntrident.shulkerboxsort;

import com.cntrident.shulkerboxsort.network.SortRequestPayload;
import com.cntrident.shulkerboxsort.network.SortResultPayload;
import com.cntrident.shulkerboxsort.server.EmptyBoxStackLimit;
import com.cntrident.shulkerboxsort.server.ServerInventorySorter;
import com.cntrident.shulkerboxsort.server.InventoryTransaction;
import com.cntrident.shulkerboxsort.server.SortCompletion;
import com.cntrident.shulkerboxsort.server.DefaultItemOrder;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public final class ShulkerBoxSort implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("shulkerbox_sort");
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    @Override public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(SortRequestPayload.TYPE, SortRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SortResultPayload.TYPE, SortResultPayload.CODEC);
        // Fabric dispatches this receiver on the server thread. Commands and
        // inventory clicks use the same thread; no interleaving during commit.
        ServerPlayNetworking.registerGlobalReceiver(SortRequestPayload.TYPE, (request, context) -> {
            SortCompletion completion = sort(context.player(), request.requestId(), request.orderedSlots());
            ServerPlayNetworking.send(context.player(), completion.result());
            completion.warning().ifPresent(context.player()::sendSystemMessage);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(command()));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> DefaultItemOrder.initialize(
                server.overworld().registryAccess(), server.overworld().enabledFeatures()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) DefaultItemOrder.initialize(server.overworld().registryAccess(), server.overworld().enabledFeatures());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_REQUEST.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_REQUEST.clear(); DefaultItemOrder.reset();
        });
        LOGGER.info("Server-side Shulker Box Sort initialized: /shulkersort (no OP required)");
    }
    /** No permission predicate: any player may sort only their own inventory. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("shulkersort").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var completion = sort(player, 0, List.of());
                    var result = completion.result();
                    // Chinese fallback keeps the command readable for unmodded clients.
                    String message = switch (result.messageKey()) {
                        case "message.shulkerbox_sort.server_completed" -> "潜影盒整理完成：整理 " + result.sorted()
                                + " 盒，腾空 " + result.emptied() + " 盒，剩余非空 " + result.remaining()
                                + " 盒，空盒堆叠释放 " + result.merged() + " 格；服务端耗时 "
                                + String.format(Locale.ROOT, "%.2f", result.micros() / 1000.0) + " ms";
                        case "message.shulkerbox_sort.cursor_not_empty" -> "请先放下光标上的物品，未修改任何物品";
                        case "message.shulkerbox_sort.inventory_only" -> "请先关闭其他容器再整理背包";
                        case "message.shulkerbox_sort.cooldown" -> "整理请求过于频繁，请稍后重试";
                        case "message.shulkerbox_sort.not_alive" -> "死亡或旁观状态不能整理";
                        case "message.shulkerbox_sort.recovery_failed" -> "其他模组阻止恢复原库存，请立即检查背包并查看服务端日志";
                        default -> "整理校验失败，未应用目标布局；请查看服务端日志";
                    };
                    player.sendSystemMessage(Component.literal(message));
                    completion.warning().ifPresent(player::sendSystemMessage);
                    return result.messageKey().endsWith("server_completed") ? 1 : 0;
                });
    }
    private static SortCompletion sort(ServerPlayer player, int id, List<Integer> order) {
        if (!player.isAlive() || player.isSpectator()) return failure(id, "not_alive");
        if (player.containerMenu != player.inventoryMenu) return failure(id, "inventory_only");
        if (!player.inventoryMenu.getCarried().isEmpty()) return failure(id, "cursor_not_empty");
        long now = System.nanoTime();
        Long previous = LAST_REQUEST.get(player.getUUID());
        if (previous != null && now - previous < 500_000_000L) return failure(id, "cooldown");
        LAST_REQUEST.put(player.getUUID(), now);
        ServerInventorySorter.Prepared prepared;
        try {
            prepared = ServerInventorySorter.prepare(player.getInventory(), order, EmptyBoxStackLimit::get);
            prepared.transaction().commit(player.getInventory());
        } catch (InventoryTransaction.RecoveryFailedException exception) {
            LOGGER.error("Inventory rollback blocked for {}", player.getUUID(), exception);
            return failure(id, "recovery_failed");
        } catch (RuntimeException exception) {
            LOGGER.error("Shulker inventory transaction failed for {}", player.getUUID(), exception);
            return failure(id, "transaction_failed");
        }
        // Synchronization failure after commit must not be reported as an
        // uncommitted transaction. Normal menu broadcasts will retry syncing.
        try { player.inventoryMenu.broadcastFullState(); }
        catch (RuntimeException syncFailure) {
            LOGGER.warn("Shulker sort committed, but immediate inventory sync failed for {}",
                    player.getUUID(), syncFailure);
        }
        return new SortCompletion(new SortResultPayload(id, SortCompletion.COMPLETED_KEY, prepared.sorted(),
                prepared.emptied(), prepared.remaining(), prepared.merged(), (System.nanoTime() - now) / 1000),
                prepared.emptyBoxStackingDisabled());
    }
    private static SortCompletion failure(int id, String reason) {
        return new SortCompletion(new SortResultPayload(id, "message.shulkerbox_sort." + reason,
                0, 0, 0, 0, 0), false);
    }
}
