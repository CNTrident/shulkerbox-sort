package com.cntrident.shulkerboxsort.session;

import com.cntrident.shulkerboxsort.compat.QuickShulkerBridge;
import com.cntrident.shulkerboxsort.compat.ItemScrollerSortBridge;
import com.cntrident.shulkerboxsort.ShulkerBoxSortClient;
import com.cntrident.shulkerboxsort.inventory.EmptyBoxMergePlanner;
import com.cntrident.shulkerboxsort.inventory.ItemStackKey;
import com.cntrident.shulkerboxsort.inventory.ShulkerScanner;
import com.cntrident.shulkerboxsort.mixin.AbstractContainerScreenAccessor;
import com.cntrident.shulkerboxsort.network.PacketSyncTracker;
import com.cntrident.shulkerboxsort.planner.PackingPlanner;
import com.cntrident.shulkerboxsort.planner.GlobalSortPlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

final class SortSession {
    private static final int SYNC_TIMEOUT_TICKS = 100;
    private static final int CLOSE_TIMEOUT_TICKS = 20;
    private static final int PREDICTION_SETTLE_TICKS = 1;
    private static final int FINALIZE_STABLE_TICKS = 4;
    private static final int MAX_FINALIZE_PASSES =
            ShulkerScanner.PLAYER_INVENTORY_SLOTS * ShulkerScanner.SHULKER_SLOTS;

    private enum State {
        RESERVE_HOTBAR,
        WAIT_RESERVE_HOTBAR,
        PLAN_OPERATION,
        BEGIN_OPERATION,
        BATCH_OPEN_SOURCE,
        BATCH_WAIT_SOURCE_OPEN,
        BATCH_PICK_SOURCE,
        BATCH_WAIT_PICK_SOURCE,
        BATCH_STAGE_SOURCE,
        BATCH_WAIT_STAGE_SOURCE,
        BATCH_CLOSE_SOURCE,
        BATCH_WAIT_SOURCE_CLOSE,
        BATCH_BEGIN_TARGET,
        BATCH_OPEN_TARGET,
        BATCH_WAIT_TARGET_OPEN,
        BATCH_PICK_STAGE,
        BATCH_WAIT_PICK_STAGE,
        BATCH_PLACE_TARGET,
        BATCH_WAIT_PLACE_TARGET,
        BATCH_STORE_REMAINDER,
        BATCH_WAIT_STORE_REMAINDER,
        BATCH_ADVANCE_TARGET,
        BATCH_CLOSE_TARGET,
        BATCH_WAIT_TARGET_CLOSE,
        BATCH_BEGIN_RETURN,
        BATCH_OPEN_RETURN_SOURCE,
        BATCH_WAIT_RETURN_SOURCE_OPEN,
        BATCH_PICK_RETURN,
        BATCH_WAIT_PICK_RETURN,
        BATCH_PLACE_RETURN,
        BATCH_WAIT_PLACE_RETURN,
        BATCH_CLOSE_RETURN_SOURCE,
        BATCH_WAIT_RETURN_SOURCE_CLOSE,
        BATCH_COMPLETE,
        OPEN_SOURCE,
        WAIT_SOURCE_OPEN,
        PICK_SOURCE,
        WAIT_PICK_SOURCE,
        STAGE_WHOLE,
        WAIT_STAGE_WHOLE,
        CLOSE_SOURCE,
        WAIT_SOURCE_CLOSE,
        OPEN_TARGET,
        WAIT_TARGET_OPEN,
        PICK_STAGE,
        WAIT_PICK_STAGE,
        PLACE_TARGET,
        WAIT_PLACE_TARGET,
        STORE_TARGET_REMAINDER,
        WAIT_STORE_TARGET_REMAINDER,
        CLOSE_TARGET,
        WAIT_TARGET_CLOSE,
        OPEN_RETURN_SOURCE,
        WAIT_RETURN_SOURCE_OPEN,
        PICK_RETURN_STAGE,
        WAIT_PICK_RETURN_STAGE,
        PLACE_RETURN_SOURCE,
        WAIT_PLACE_RETURN_SOURCE,
        CLOSE_RETURN_SOURCE,
        WAIT_RETURN_SOURCE_CLOSE,
        BEGIN_SWAP_CYCLE,
        OPEN_SWAP_BOX,
        WAIT_SWAP_OPEN,
        EXECUTE_SWAP,
        WAIT_SWAP,
        CLOSE_SWAP_BOX,
        WAIT_SWAP_CLOSE,
        PREPARE_BOX_FINALIZE,
        OPEN_FINALIZE_BOX,
        WAIT_FINALIZE_OPEN,
        RUN_ITEMSCROLLER_SORT,
        WAIT_ITEMSCROLLER_SORT,
        CLOSE_FINALIZE_BOX,
        WAIT_FINALIZE_CLOSE,
        COMPLETE_FINALIZE_PASS,
        RESTORE_HOTBAR,
        WAIT_RESTORE_HOTBAR,
        PREPARE_EMPTY_MERGES,
        BEGIN_EMPTY_MERGE,
        PICK_EMPTY_SOURCE,
        WAIT_PICK_EMPTY_SOURCE,
        PLACE_EMPTY_TARGET,
        WAIT_PLACE_EMPTY_TARGET,
        RETURN_EMPTY_REMAINDER,
        WAIT_RETURN_EMPTY_REMAINDER,
        ABORT_RECOVERY_WAIT,
        ABORT_WAIT_INVENTORY,
        ABORT_RESTORE_HOTBAR,
        ABORT_WAIT_HOTBAR_RESTORE,
        STOPPED
    }

    private List<Integer> stagingInventorySlots = List.of();
    private int stagingInventorySlot;
    private List<PackingPlanner.Transfer<ItemStackKey>> transfers = List.of();
    private int emptiedBoxes;
    private int remainingBoxes;
    private int crossBoxMoves;
    private List<GlobalSortPlanner.SwapCycle<ItemStackKey>> swapCycles = List.of();
    private int swapCycleIndex;
    private int swapStepIndex;
    private State afterSwapCloseState;
    private final int workspaceHotbarSlot;
    private final int borrowedDestinationSlot;
    private final ItemStackKey borrowedOriginalKey;
    private final int borrowedOriginalCount;
    private final boolean borrowedEligibleShulker;
    private boolean backgroundMode;
    private boolean expectingHiddenContainerScreen;
    private Screen hiddenContainerScreen;
    private List<Integer> finalizeBoxSlots = List.of();
    private int finalizeBoxIndex;
    private int finalizePassCount;
    private int finalizeOccupiedBefore;
    private boolean finalizePassCreatedGap;
    private List<ItemStack> finalizeLayoutBefore = List.of();
    private long finalizeSortRevision;
    private int finalizeSortContainerId;
    private int finalizeSortSentAt;
    private int finalizeLastStateId;
    private int finalizeStableTicks;
    private boolean finalizePredictionChanged;

    private State state = State.BEGIN_OPERATION;
    private int age;
    private int deadline;
    private long waitRevision;
    private int waitContainerId;
    private int waitStateId;
    private int clickSentAt;
    private int openContainerId = Integer.MIN_VALUE;
    private int transferIndex;
    private PackingPlanner.Transfer<ItemStackKey> transfer;
    private int pickedCount;
    private int targetBeforeCount;
    private List<EmptyBoxMergePlanner.Merge> emptyMerges = List.of();
    private int emptyMergeIndex;
    private EmptyBoxMergePlanner.Merge emptyMerge;
    private int emptyMergeCount;
    private int emptyTargetBeforeCount;
    private Component abortMessage;
    private Batch batch;
    private int batchJobIndex;
    private int targetGroupIndex;
    private int targetTransferIndex;
    private int returnJobIndex;
    private BatchJob activeJob;
    private PackingPlanner.Transfer<ItemStackKey> batchTransfer;
    private int batchTargetBeforeCount;

    private static final class BatchJob {
        private final int sourceBoxSlot;
        private final ItemStackKey key;
        private final int stagingSlot;
        private final List<PackingPlanner.Transfer<ItemStackKey>> moves = new ArrayList<>();
        private int originalCount;
        private int stagedCount;

        private BatchJob(int sourceBoxSlot, ItemStackKey key, int stagingSlot) {
            this.sourceBoxSlot = sourceBoxSlot;
            this.key = key;
            this.stagingSlot = stagingSlot;
        }

        private int plannedAmount() {
            return moves.stream().mapToInt(PackingPlanner.Transfer::amount).sum();
        }
    }

    private record TargetGroup(int targetInventorySlot,
                               List<PackingPlanner.Transfer<ItemStackKey>> moves) {
    }

    private record Batch(int sourceInventorySlot, int endTransferIndex,
                         List<BatchJob> jobs, List<TargetGroup> targets,
                         Map<Integer, BatchJob> jobsBySourceSlot) {
    }

    private SortSession(int workspaceHotbarSlot, int borrowedDestinationSlot,
                        ItemStackKey borrowedOriginalKey, int borrowedOriginalCount,
                        boolean borrowedEligibleShulker) {
        this.workspaceHotbarSlot = workspaceHotbarSlot;
        this.borrowedDestinationSlot = borrowedDestinationSlot;
        this.borrowedOriginalKey = borrowedOriginalKey;
        this.borrowedOriginalCount = borrowedOriginalCount;
        this.borrowedEligibleShulker = borrowedEligibleShulker;
        this.state = borrowedDestinationSlot < 0 ? State.PLAN_OPERATION : State.RESERVE_HOTBAR;
    }

    static SortSession create(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !(client.screen instanceof InventoryScreen)
                || player.containerMenu != player.inventoryMenu) {
            message(player, "message.shulkerbox_sort.inventory_only");
            return null;
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            message(player, "message.shulkerbox_sort.cursor_not_empty");
            return null;
        }

        Inventory inventory = player.getInventory();
        int emptySlot = -1;
        for (int slot = 0; slot < ShulkerScanner.PLAYER_INVENTORY_SLOTS; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                emptySlot = slot;
                break;
            }
        }
        if (emptySlot < 0) {
            message(player, "message.shulkerbox_sort.inventory_full");
            return null;
        }
        int workspace = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                workspace = slot;
                break;
            }
        }
        int borrowedDestination = -1;
        ItemStackKey borrowedKey = null;
        int borrowedCount = 0;
        boolean borrowedShulker = false;
        if (workspace < 0) {
            workspace = 0;
            borrowedDestination = emptySlot;
            ItemStack borrowed = inventory.getItem(workspace);
            borrowedKey = new ItemStackKey(borrowed);
            borrowedCount = borrowed.getCount();
            borrowedShulker = ShulkerScanner.isVanillaShulkerBox(borrowed)
                    && !ShulkerScanner.isNamedOrSpecial(borrowed) && borrowed.getCount() == 1;
        }
        message(player, "message.shulkerbox_sort.started");
        return new SortSession(workspace, borrowedDestination, borrowedKey, borrowedCount, borrowedShulker);
    }

    void enterBackgroundMode(Minecraft client) {
        backgroundMode = true;
        client.setScreen(null);
    }

    boolean consumeExpectedContainerScreen(Screen screen) {
        if (!backgroundMode || !expectingHiddenContainerScreen
                || !(screen instanceof AbstractContainerScreen<?> containerScreen)
                || screen instanceof InventoryScreen) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || containerScreen.getMenu() != client.player.containerMenu
                || containerScreen.getMenu().slots.size() < ShulkerScanner.SHULKER_SLOTS + 36) {
            return false;
        }
        expectingHiddenContainerScreen = false;
        hiddenContainerScreen = containerScreen;
        return true;
    }

    boolean tick(Minecraft client) {
        if (state == State.STOPPED) {
            return false;
        }
        if (client.player == null || client.getConnection() == null) {
            state = State.STOPPED;
            return false;
        }

        LocalPlayer player = client.player;
        age++;
        if (state == State.ABORT_RECOVERY_WAIT) {
            finishAbortRecovery(player);
            return state != State.STOPPED;
        }
        if (player.isDeadOrDying()) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return state != State.STOPPED;
        }
        Screen screen = client.screen;
        if (backgroundMode && screen != null) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return state != State.STOPPED;
        }
        if (!backgroundMode && screen != null && !(screen instanceof AbstractContainerScreen<?>)) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return state != State.STOPPED;
        }

        for (int immediateSteps = 0; immediateSteps < 64 && state != State.STOPPED; immediateSteps++) {
            State stateBeforeStep = state;
            switch (state) {
            case RESERVE_HOTBAR -> reserveHotbar(client);
            case WAIT_RESERVE_HOTBAR -> waitForClick(client,
                    () -> player.getInventory().getItem(workspaceHotbarSlot).isEmpty()
                            && inventoryStackMatches(player, borrowedDestinationSlot,
                            borrowedOriginalKey, borrowedOriginalCount), State.PLAN_OPERATION);
            case PLAN_OPERATION -> planOperation(client);
            case BEGIN_OPERATION -> beginBatch();
            case BATCH_OPEN_SOURCE -> openBox(client, batch.sourceInventorySlot(), State.BATCH_WAIT_SOURCE_OPEN);
            case BATCH_WAIT_SOURCE_OPEN -> waitForOpenContainer(client, State.BATCH_PICK_SOURCE);
            case BATCH_PICK_SOURCE -> batchPickSource(client);
            case BATCH_WAIT_PICK_SOURCE -> waitForClick(client,
                    () -> carriedMatches(player, activeJob.key, activeJob.originalCount),
                    State.BATCH_STAGE_SOURCE);
            case BATCH_STAGE_SOURCE -> batchStageSource(client);
            case BATCH_WAIT_STAGE_SOURCE -> waitForClick(client,
                    () -> player.containerMenu.getCarried().isEmpty()
                            && inventoryStackMatches(player, activeJob.stagingSlot,
                            activeJob.key, activeJob.originalCount), State.BATCH_PICK_SOURCE);
            case BATCH_CLOSE_SOURCE -> closeContainer(player, State.BATCH_WAIT_SOURCE_CLOSE);
            case BATCH_WAIT_SOURCE_CLOSE -> waitForClosed(player, State.BATCH_BEGIN_TARGET);
            case BATCH_BEGIN_TARGET -> batchBeginTarget();
            case BATCH_OPEN_TARGET -> openBox(client,
                    batch.targets().get(targetGroupIndex).targetInventorySlot(), State.BATCH_WAIT_TARGET_OPEN);
            case BATCH_WAIT_TARGET_OPEN -> waitForOpenContainer(client, State.BATCH_PICK_STAGE);
            case BATCH_PICK_STAGE -> batchPickStage(client);
            case BATCH_WAIT_PICK_STAGE -> waitForClick(client,
                    () -> carriedMatches(player, activeJob.key, activeJob.stagedCount)
                            && player.getInventory().getItem(activeJob.stagingSlot).isEmpty(),
                    State.BATCH_PLACE_TARGET);
            case BATCH_PLACE_TARGET -> batchPlaceTarget(client);
            case BATCH_WAIT_PLACE_TARGET -> waitForBatchTarget(player, client);
            case BATCH_STORE_REMAINDER -> clickPlayerInventorySlot(client, activeJob.stagingSlot, 0,
                    State.BATCH_WAIT_STORE_REMAINDER);
            case BATCH_WAIT_STORE_REMAINDER -> waitForClick(client,
                    () -> player.containerMenu.getCarried().isEmpty()
                            && inventoryStackMatches(player, activeJob.stagingSlot,
                            activeJob.key, activeJob.stagedCount), State.BATCH_ADVANCE_TARGET);
            case BATCH_ADVANCE_TARGET -> batchAdvanceTarget();
            case BATCH_CLOSE_TARGET -> closeContainer(player, State.BATCH_WAIT_TARGET_CLOSE);
            case BATCH_WAIT_TARGET_CLOSE -> waitForClosed(player, State.BATCH_BEGIN_TARGET);
            case BATCH_BEGIN_RETURN -> batchBeginReturn();
            case BATCH_OPEN_RETURN_SOURCE -> openBox(client, batch.sourceInventorySlot(),
                    State.BATCH_WAIT_RETURN_SOURCE_OPEN);
            case BATCH_WAIT_RETURN_SOURCE_OPEN -> waitForOpenContainer(client, State.BATCH_PICK_RETURN);
            case BATCH_PICK_RETURN -> batchPickReturn(client);
            case BATCH_WAIT_PICK_RETURN -> waitForClick(client,
                    () -> carriedMatches(player, activeJob.key, activeJob.stagedCount)
                            && player.getInventory().getItem(activeJob.stagingSlot).isEmpty(),
                    State.BATCH_PLACE_RETURN);
            case BATCH_PLACE_RETURN -> batchPlaceReturn(client);
            case BATCH_WAIT_PLACE_RETURN -> waitForClick(client,
                    () -> player.containerMenu.getCarried().isEmpty()
                            && containerStackMatches(player.containerMenu, activeJob.sourceBoxSlot,
                            activeJob.key, activeJob.stagedCount), State.BATCH_PICK_RETURN);
            case BATCH_CLOSE_RETURN_SOURCE -> closeContainer(player, State.BATCH_WAIT_RETURN_SOURCE_CLOSE);
            case BATCH_WAIT_RETURN_SOURCE_CLOSE -> waitForClosed(player, State.BATCH_COMPLETE);
            case BATCH_COMPLETE -> completeBatch();
            case OPEN_SOURCE -> openBox(client, transfer.sourceInventorySlot(), State.WAIT_SOURCE_OPEN);
            case WAIT_SOURCE_OPEN -> waitForOpenContainer(client, State.PICK_SOURCE);
            case PICK_SOURCE -> pickSource(client);
            case WAIT_PICK_SOURCE -> waitForClick(client,
                    () -> carriedMatches(player, transfer.key(), pickedCount),
                    State.STAGE_WHOLE);
            case STAGE_WHOLE -> clickPlayerInventorySlot(client, stagingInventorySlot, 0,
                    State.WAIT_STAGE_WHOLE);
            case WAIT_STAGE_WHOLE -> waitForClick(client,
                    () -> player.containerMenu.getCarried().isEmpty()
                            && inventoryStackMatches(player, stagingInventorySlot, transfer.key(), pickedCount),
                    State.CLOSE_SOURCE);
            case CLOSE_SOURCE -> closeContainer(player, State.WAIT_SOURCE_CLOSE);
            case WAIT_SOURCE_CLOSE -> waitForClosed(player, State.OPEN_TARGET);
            case OPEN_TARGET -> openBox(client, transfer.targetInventorySlot(), State.WAIT_TARGET_OPEN);
            case WAIT_TARGET_OPEN -> waitForOpenContainer(client, State.PICK_STAGE);
            case PICK_STAGE -> pickStagedForTarget(client);
            case WAIT_PICK_STAGE -> waitForClick(client,
                    () -> carriedMatches(player, transfer.key(), pickedCount)
                            && player.getInventory().getItem(stagingInventorySlot).isEmpty(),
                    State.PLACE_TARGET);
            case PLACE_TARGET -> placeTarget(client);
            case WAIT_PLACE_TARGET -> waitForClick(client,
                    () -> carriedMatches(player, transfer.key(), pickedCount - transfer.amount())
                            && containerStackMatches(player.containerMenu, transfer.targetBoxSlot(), transfer.key(),
                            targetBeforeCount + transfer.amount()),
                    pickedCount == transfer.amount()
                            ? State.CLOSE_TARGET : State.STORE_TARGET_REMAINDER);
            case STORE_TARGET_REMAINDER -> clickPlayerInventorySlot(client, stagingInventorySlot, 0,
                    State.WAIT_STORE_TARGET_REMAINDER);
            case WAIT_STORE_TARGET_REMAINDER -> waitForClick(client,
                    () -> player.containerMenu.getCarried().isEmpty()
                            && inventoryStackMatches(player, stagingInventorySlot, transfer.key(),
                            pickedCount - transfer.amount()),
                    State.CLOSE_TARGET);
            case CLOSE_TARGET -> closeContainer(player, State.WAIT_TARGET_CLOSE);
            case WAIT_TARGET_CLOSE -> waitForClosed(player,
                    pickedCount == transfer.amount() ? State.BEGIN_OPERATION : State.OPEN_RETURN_SOURCE);
            case OPEN_RETURN_SOURCE -> openBox(client, transfer.sourceInventorySlot(),
                    State.WAIT_RETURN_SOURCE_OPEN);
            case WAIT_RETURN_SOURCE_OPEN -> waitForOpenContainer(client, State.PICK_RETURN_STAGE);
            case PICK_RETURN_STAGE -> clickPlayerInventorySlot(client, stagingInventorySlot, 0,
                    State.WAIT_PICK_RETURN_STAGE);
            case WAIT_PICK_RETURN_STAGE -> waitForClick(client,
                    () -> carriedMatches(player, transfer.key(), pickedCount - transfer.amount())
                            && player.getInventory().getItem(stagingInventorySlot).isEmpty(),
                    State.PLACE_RETURN_SOURCE);
            case PLACE_RETURN_SOURCE -> placeReturnSource(client);
            case WAIT_PLACE_RETURN_SOURCE -> waitForClick(client,
                    () -> player.containerMenu.getCarried().isEmpty()
                            && containerStackMatches(player.containerMenu, transfer.sourceBoxSlot(), transfer.key(),
                            pickedCount - transfer.amount()),
                    State.CLOSE_RETURN_SOURCE);
            case CLOSE_RETURN_SOURCE -> closeContainer(player, State.WAIT_RETURN_SOURCE_CLOSE);
            case WAIT_RETURN_SOURCE_CLOSE -> waitForClosed(player, State.BEGIN_OPERATION);
            case BEGIN_SWAP_CYCLE -> beginSwapCycle();
            case OPEN_SWAP_BOX -> openCurrentSwapBox(client);
            case WAIT_SWAP_OPEN -> waitForOpenContainer(client, State.EXECUTE_SWAP);
            case EXECUTE_SWAP -> executeSwap(client);
            case WAIT_SWAP -> waitForSwap(client);
            case CLOSE_SWAP_BOX -> closeContainer(player, State.WAIT_SWAP_CLOSE);
            case WAIT_SWAP_CLOSE -> waitForClosed(player, afterSwapCloseState);
            case PREPARE_BOX_FINALIZE -> prepareBoxFinalize(player);
            case OPEN_FINALIZE_BOX -> openFinalizeBox(client);
            case WAIT_FINALIZE_OPEN -> waitForOpenContainer(client, State.RUN_ITEMSCROLLER_SORT);
            case RUN_ITEMSCROLLER_SORT -> runItemScrollerSort(client);
            case WAIT_ITEMSCROLLER_SORT -> waitForItemScrollerSort(client);
            case CLOSE_FINALIZE_BOX -> closeContainer(player, State.WAIT_FINALIZE_CLOSE);
            case WAIT_FINALIZE_CLOSE -> waitForClosed(player, State.OPEN_FINALIZE_BOX);
            case COMPLETE_FINALIZE_PASS -> completeFinalizePass(client);
            case RESTORE_HOTBAR -> restoreHotbar(client);
            case WAIT_RESTORE_HOTBAR -> waitForClick(client,
                    () -> player.getInventory().getItem(borrowedDestinationSlot).isEmpty()
                            && !player.getInventory().getItem(workspaceHotbarSlot).isEmpty()
                            && player.inventoryMenu.getCarried().isEmpty(), State.PREPARE_EMPTY_MERGES);
            case PREPARE_EMPTY_MERGES -> prepareEmptyMerges(player);
            case BEGIN_EMPTY_MERGE -> beginEmptyMerge(player);
            case PICK_EMPTY_SOURCE -> pickEmptySource(client);
            case WAIT_PICK_EMPTY_SOURCE -> waitForClick(client,
                    () -> carriedMatches(player, emptyMerge.key(), emptyMergeCount)
                            && player.getInventory().getItem(emptyMerge.sourceInventorySlot()).isEmpty(),
                    State.PLACE_EMPTY_TARGET);
            case PLACE_EMPTY_TARGET -> placeEmptyTarget(client);
            case WAIT_PLACE_EMPTY_TARGET -> waitForClick(client,
                    () -> carriedMatches(player, emptyMerge.key(),
                            emptyMergeCount - emptyMerge.amount())
                            && inventoryStackMatches(player, emptyMerge.targetInventorySlot(),
                            emptyMerge.key(), emptyTargetBeforeCount + emptyMerge.amount()),
                    emptyMergeCount == emptyMerge.amount()
                            ? State.BEGIN_EMPTY_MERGE : State.RETURN_EMPTY_REMAINDER);
            case RETURN_EMPTY_REMAINDER -> returnEmptyRemainder(client);
            case WAIT_RETURN_EMPTY_REMAINDER -> waitForClick(client,
                    () -> player.inventoryMenu.getCarried().isEmpty()
                            && inventoryStackMatches(player, emptyMerge.sourceInventorySlot(),
                            emptyMerge.key(), emptyMergeCount - emptyMerge.amount()),
                    State.BEGIN_EMPTY_MERGE);
            case ABORT_RECOVERY_WAIT -> finishAbortRecovery(player);
            case ABORT_WAIT_INVENTORY -> waitForAbortInventory(player);
            case ABORT_RESTORE_HOTBAR -> restoreHotbarAfterAbort(client);
            case ABORT_WAIT_HOTBAR_RESTORE -> waitForAbortHotbarRestore(player);
            case STOPPED -> {
                return false;
            }
            }
            // Waiting states keep the same value until a packet arrives or a
            // prediction settles. Immediate bookkeeping states can continue
            // in this tick, so the next click is sent as soon as it is safe.
            if (state == stateBeforeStep) {
                break;
            }
        }
        return state != State.STOPPED;
    }

    private void beginBatch() {
        if (transferIndex >= transfers.size()) {
            state = State.BEGIN_SWAP_CYCLE;
            return;
        }

        int sourceInventorySlot = transfers.get(transferIndex).sourceInventorySlot();
        Map<Integer, BatchJob> jobs = new LinkedHashMap<>();
        Set<Integer> sourceSlots = new HashSet<>();
        Set<Integer> localTargetSlots = new HashSet<>();
        int end = transferIndex;
        while (end < transfers.size()) {
            PackingPlanner.Transfer<ItemStackKey> move = transfers.get(end);
            if (move.sourceInventorySlot() != sourceInventorySlot) {
                break;
            }
            Set<Integer> nextSources = new HashSet<>(sourceSlots);
            Set<Integer> nextTargets = new HashSet<>(localTargetSlots);
            nextSources.add(move.sourceBoxSlot());
            if (move.targetInventorySlot() == sourceInventorySlot) {
                nextTargets.add(move.targetBoxSlot());
            }
            if (nextSources.stream().anyMatch(nextTargets::contains)) {
                break;
            }
            BatchJob job = jobs.get(move.sourceBoxSlot());
            if (job == null) {
                if (jobs.size() >= stagingInventorySlots.size()) {
                    break;
                }
                job = new BatchJob(move.sourceBoxSlot(), move.key(),
                        stagingInventorySlots.get(jobs.size()));
                jobs.put(move.sourceBoxSlot(), job);
            } else if (!job.key.equals(move.key())) {
                break;
            }
            job.moves.add(move);
            sourceSlots = nextSources;
            localTargetSlots = nextTargets;
            end++;
        }

        Map<Integer, List<PackingPlanner.Transfer<ItemStackKey>>> groupedTargets = new LinkedHashMap<>();
        for (BatchJob job : jobs.values()) {
            for (PackingPlanner.Transfer<ItemStackKey> move : job.moves) {
                groupedTargets.computeIfAbsent(move.targetInventorySlot(), ignored -> new ArrayList<>())
                        .add(move);
            }
        }
        List<TargetGroup> targets = groupedTargets.entrySet().stream()
                .map(entry -> new TargetGroup(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        batch = new Batch(sourceInventorySlot, end, List.copyOf(jobs.values()),
                targets, Map.copyOf(jobs));
        batchJobIndex = 0;
        targetGroupIndex = 0;
        state = State.BATCH_OPEN_SOURCE;
    }

    private void reserveHotbar(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || player.containerMenu != player.inventoryMenu
                || borrowedDestinationSlot < 0
                || !inventoryStackMatches(player, workspaceHotbarSlot,
                borrowedOriginalKey, borrowedOriginalCount)
                || !player.getInventory().getItem(borrowedDestinationSlot).isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.hotbar_staging_failed"));
            return;
        }
        sendInventorySwap(client, borrowedDestinationSlot, workspaceHotbarSlot,
                State.WAIT_RESERVE_HOTBAR);
    }

    private void planOperation(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || player.containerMenu != player.inventoryMenu
                || !player.getInventory().getItem(workspaceHotbarSlot).isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.hotbar_staging_failed"));
            return;
        }
        if (!ItemScrollerSortBridge.isAvailable()) {
            abort(client, Component.translatable("message.shulkerbox_sort.itemscroller_unavailable"));
            return;
        }
        ShulkerScanner.ScanResult scan = ShulkerScanner.scan(player.getInventory());
        if (!scan.stagingInventorySlots().contains(workspaceHotbarSlot)) {
            abort(client, Component.translatable("message.shulkerbox_sort.hotbar_staging_failed"));
            return;
        }
        final GlobalSortPlanner.Plan<ItemStackKey> plan;
        try {
            plan = GlobalSortPlanner.plan(scan.boxes(), ItemScrollerSortBridge.comparator());
        } catch (RuntimeException exception) {
            ShulkerBoxSortClient.LOGGER.error("Failed to build global shulker sort plan", exception);
            abort(client, Component.translatable("message.shulkerbox_sort.itemscroller_unavailable"));
            return;
        }
        stagingInventorySlots = scan.stagingInventorySlots();
        stagingInventorySlot = workspaceHotbarSlot;
        transfers = plan.merges();
        swapCycles = plan.swapCycles();
        transferIndex = 0;
        swapCycleIndex = 0;
        batch = null;
        emptiedBoxes += plan.emptiedBoxes();
        remainingBoxes = plan.remainingNonEmptyBoxes();
        crossBoxMoves += plan.crossBoxMoves();
        ShulkerBoxSortClient.LOGGER.info(
                "Global shulker plan: eligible={}, ignored={}, includedMixedFull={}, lockedHomogeneousFull={}, merges={}, cycles={}, pages={}, stagingSlots={}",
                scan.boxes().size(), scan.ignoredNamedOrSpecial(), scan.includedMixedFullBoxes(),
                scan.lockedHomogeneousFullBoxes(),
                transfers.size(), swapCycles.size(), plan.pageForBox(), stagingInventorySlots);
        state = State.BEGIN_OPERATION;
    }

    private void beginSwapCycle() {
        if (swapCycleIndex >= swapCycles.size()) {
            state = State.PREPARE_BOX_FINALIZE;
            return;
        }
        swapStepIndex = 0;
        state = State.OPEN_SWAP_BOX;
    }

    private GlobalSortPlanner.SwapStep<ItemStackKey> currentSwapStep() {
        return swapCycles.get(swapCycleIndex).steps().get(swapStepIndex);
    }

    private void openCurrentSwapBox(Minecraft client) {
        openBox(client, currentSwapStep().slot().inventorySlot(), State.WAIT_SWAP_OPEN);
    }

    private void executeSwap(Minecraft client) {
        LocalPlayer player = client.player;
        GlobalSortPlanner.SwapStep<ItemStackKey> step = currentSwapStep();
        if (player == null || !validOpenBoxMenu(player.containerMenu)
                || !stackMatches(player.containerMenu.getSlot(step.slot().boxSlot()).getItem(), step.slotBefore())
                || !stackMatches(player.getInventory().getItem(workspaceHotbarSlot), step.registerBefore())
                || !player.containerMenu.getCarried().isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.layout_changed"));
            return;
        }
        sendSwap(client, player.containerMenu, step.slot().boxSlot(), workspaceHotbarSlot, State.WAIT_SWAP);
    }

    private void waitForSwap(Minecraft client) {
        LocalPlayer player = client.player;
        GlobalSortPlanner.SwapStep<ItemStackKey> step = currentSwapStep();
        BooleanSupplier validation = () -> stackMatches(
                player.containerMenu.getSlot(step.slot().boxSlot()).getItem(), step.slotAfter())
                && stackMatches(player.getInventory().getItem(workspaceHotbarSlot), step.registerAfter())
                && player.containerMenu.getCarried().isEmpty();
        if (!clickSettled(player, validation)) {
            abortIfClickTimedOut(client, player, State.EXECUTE_SWAP);
            return;
        }

        swapStepIndex++;
        List<GlobalSortPlanner.SwapStep<ItemStackKey>> steps = swapCycles.get(swapCycleIndex).steps();
        if (swapStepIndex >= steps.size()) {
            swapCycleIndex++;
            afterSwapCloseState = State.BEGIN_SWAP_CYCLE;
            state = State.CLOSE_SWAP_BOX;
            return;
        }
        if (steps.get(swapStepIndex).slot().inventorySlot() == step.slot().inventorySlot()) {
            state = State.EXECUTE_SWAP;
        } else {
            afterSwapCloseState = State.OPEN_SWAP_BOX;
            state = State.CLOSE_SWAP_BOX;
        }
    }

    private void prepareBoxFinalize(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu
                || !player.inventoryMenu.getCarried().isEmpty()
                || !player.getInventory().getItem(workspaceHotbarSlot).isEmpty()) {
            abort(Minecraft.getInstance(), Component.translatable(
                    "message.shulkerbox_sort.safe_abort"));
            return;
        }
        if (!ItemScrollerSortBridge.isSorterAvailable()) {
            abort(Minecraft.getInstance(), Component.translatable(
                    "message.shulkerbox_sort.itemscroller_unavailable"));
            return;
        }
        finalizeBoxSlots = ShulkerScanner.sortableNonEmptyBoxSlots(player.getInventory());
        finalizeBoxIndex = 0;
        finalizePassCreatedGap = false;
        state = State.OPEN_FINALIZE_BOX;
    }

    private void openFinalizeBox(Minecraft client) {
        if (finalizeBoxIndex >= finalizeBoxSlots.size()) {
            state = State.COMPLETE_FINALIZE_PASS;
            return;
        }
        openBox(client, finalizeBoxSlots.get(finalizeBoxIndex), State.WAIT_FINALIZE_OPEN);
    }

    private void runItemScrollerSort(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !validOpenBoxMenu(player.containerMenu)
                || !(hiddenContainerScreen instanceof AbstractContainerScreen<?> screen)
                || screen.getMenu() != player.containerMenu
                || !player.containerMenu.getCarried().isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }

        finalizeLayoutBefore = copyBoxLayout(player.containerMenu);
        finalizeOccupiedBefore = occupiedBoxSlots(player.containerMenu);
        finalizeSortRevision = PacketSyncTracker.revision();
        finalizeSortContainerId = player.containerMenu.containerId;
        finalizeSortSentAt = age;
        finalizeLastStateId = player.containerMenu.getStateId();
        finalizeStableTicks = 0;
        ((AbstractContainerScreenAccessor) screen).shulkerboxSort$setHoveredSlot(
                player.containerMenu.getSlot(0));
        try {
            ItemScrollerSortBridge.sortInventory(screen);
        } catch (RuntimeException exception) {
            ShulkerBoxSortClient.LOGGER.error("Item Scroller per-box finalization failed", exception);
            abort(client, Component.translatable("message.shulkerbox_sort.itemscroller_unavailable"));
            return;
        }
        finalizePredictionChanged = !boxLayoutMatches(player.containerMenu, finalizeLayoutBefore);
        deadline = age + SYNC_TIMEOUT_TICKS;
        state = State.WAIT_ITEMSCROLLER_SORT;
    }

    private void waitForItemScrollerSort(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || player.containerMenu.containerId != finalizeSortContainerId
                || !validOpenBoxMenu(player.containerMenu)
                || !player.containerMenu.getCarried().isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }

        int currentStateId = player.containerMenu.getStateId();
        if (currentStateId != finalizeLastStateId) {
            finalizeLastStateId = currentStateId;
            finalizeStableTicks = 0;
        } else {
            finalizeStableTicks++;
        }
        if (!finalizePredictionChanged
                && !boxLayoutMatches(player.containerMenu, finalizeLayoutBefore)) {
            finalizePredictionChanged = true;
        }
        boolean serverConfirmed = !finalizePredictionChanged
                || PacketSyncTracker.advancedFor(finalizeSortRevision, finalizeSortContainerId);
        boolean itemScrollerFinished;
        try {
            itemScrollerFinished = !ItemScrollerSortBridge.isSortPending();
        } catch (RuntimeException exception) {
            abort(client, Component.translatable("message.shulkerbox_sort.itemscroller_unavailable"));
            return;
        }
        if (age > finalizeSortSentAt && itemScrollerFinished && serverConfirmed
                && finalizeStableTicks >= FINALIZE_STABLE_TICKS) {
            if (occupiedBoxSlots(player.containerMenu) < finalizeOccupiedBefore) {
                finalizePassCreatedGap = true;
            }
            finalizeBoxIndex++;
            state = State.CLOSE_FINALIZE_BOX;
            return;
        }
        if (age >= deadline) {
            abort(client, Component.translatable("message.shulkerbox_sort.sync_timeout"));
        }
    }

    private void completeFinalizePass(Minecraft client) {
        if (!finalizePassCreatedGap) {
            state = State.RESTORE_HOTBAR;
            return;
        }
        finalizePassCount++;
        if (finalizePassCount > MAX_FINALIZE_PASSES) {
            abort(client, Component.translatable("message.shulkerbox_sort.layout_changed"));
            return;
        }
        // Item Scroller merged at least one pair inside a box. Rebuild the
        // global virtual table so the newly-created holes are filled, then run
        // another per-box pass. Every repeating pass must remove at least one
        // occupied stack, so this process is finite.
        state = State.PLAN_OPERATION;
    }

    private void restoreHotbar(Minecraft client) {
        LocalPlayer player = client.player;
        if (borrowedDestinationSlot < 0) {
            state = State.PREPARE_EMPTY_MERGES;
            return;
        }
        if (player == null || player.containerMenu != player.inventoryMenu
                || !player.getInventory().getItem(workspaceHotbarSlot).isEmpty()
                || !borrowedStackMatches(player.getInventory().getItem(borrowedDestinationSlot))) {
            abort(client, Component.translatable("message.shulkerbox_sort.hotbar_restore_failed"));
            return;
        }
        sendInventorySwap(client, borrowedDestinationSlot, workspaceHotbarSlot,
                State.WAIT_RESTORE_HOTBAR);
    }

    private void batchPickSource(Minecraft client) {
        if (batchJobIndex >= batch.jobs().size()) {
            state = State.BATCH_CLOSE_SOURCE;
            return;
        }
        LocalPlayer player = client.player;
        activeJob = batch.jobs().get(batchJobIndex);
        ItemStack source = player.containerMenu.getSlot(activeJob.sourceBoxSlot).getItem();
        if (!validOpenBoxMenu(player.containerMenu) || !activeJob.key.matches(source)
                || source.getCount() < activeJob.plannedAmount()
                || !player.getInventory().getItem(activeJob.stagingSlot).isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        activeJob.originalCount = source.getCount();
        activeJob.stagedCount = source.getCount();
        clickContainerSlot(client, activeJob.sourceBoxSlot, 0, State.BATCH_WAIT_PICK_SOURCE);
    }

    private void batchBeginTarget() {
        if (targetGroupIndex >= batch.targets().size()) {
            state = State.BATCH_BEGIN_RETURN;
            return;
        }
        targetTransferIndex = 0;
        state = State.BATCH_OPEN_TARGET;
    }

    private void batchStageSource(Minecraft client) {
        clickPlayerInventorySlot(client, activeJob.stagingSlot, 0, State.BATCH_WAIT_STAGE_SOURCE);
        batchJobIndex++;
    }

    private void batchPickStage(Minecraft client) {
        TargetGroup group = batch.targets().get(targetGroupIndex);
        if (targetTransferIndex >= group.moves().size()) {
            targetGroupIndex++;
            state = State.BATCH_CLOSE_TARGET;
            return;
        }
        batchTransfer = group.moves().get(targetTransferIndex);
        activeJob = batch.jobsBySourceSlot().get(batchTransfer.sourceBoxSlot());
        if (activeJob == null || activeJob.stagedCount < batchTransfer.amount()
                || !inventoryStackMatches(client.player, activeJob.stagingSlot,
                activeJob.key, activeJob.stagedCount)) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        clickPlayerInventorySlot(client, activeJob.stagingSlot, 0, State.BATCH_WAIT_PICK_STAGE);
    }

    private void batchPlaceTarget(Minecraft client) {
        LocalPlayer player = client.player;
        ItemStack target = player.containerMenu.getSlot(batchTransfer.targetBoxSlot()).getItem();
        if (!validOpenBoxMenu(player.containerMenu)
                || !target.isEmpty() && !batchTransfer.key().matches(target)) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        batchTargetBeforeCount = target.isEmpty() ? 0 : target.getCount();
        if (batchTargetBeforeCount + batchTransfer.amount()
                > batchTransfer.key().template().getMaxStackSize()) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        clickContainerSlot(client, batchTransfer.targetBoxSlot(), 0, State.BATCH_WAIT_PLACE_TARGET);
    }

    private void waitForBatchTarget(LocalPlayer player, Minecraft client) {
        int remaining = activeJob.stagedCount - batchTransfer.amount();
        BooleanSupplier validation = () -> carriedMatches(player, activeJob.key, remaining)
                && containerStackMatches(player.containerMenu, batchTransfer.targetBoxSlot(),
                batchTransfer.key(), batchTargetBeforeCount + batchTransfer.amount());
        if (clickSettled(player, validation)) {
            activeJob.stagedCount = remaining;
            state = remaining == 0 ? State.BATCH_ADVANCE_TARGET : State.BATCH_STORE_REMAINDER;
            return;
        }
        abortIfClickTimedOut(client, player, State.BATCH_ADVANCE_TARGET);
    }

    private void batchAdvanceTarget() {
        targetTransferIndex++;
        state = State.BATCH_PICK_STAGE;
    }

    private void batchBeginReturn() {
        boolean hasRemainder = batch.jobs().stream().anyMatch(job -> job.stagedCount > 0);
        if (!hasRemainder) {
            state = State.BATCH_COMPLETE;
            return;
        }
        returnJobIndex = 0;
        state = State.BATCH_OPEN_RETURN_SOURCE;
    }

    private void batchPickReturn(Minecraft client) {
        while (returnJobIndex < batch.jobs().size()
                && batch.jobs().get(returnJobIndex).stagedCount == 0) {
            returnJobIndex++;
        }
        if (returnJobIndex >= batch.jobs().size()) {
            state = State.BATCH_CLOSE_RETURN_SOURCE;
            return;
        }
        activeJob = batch.jobs().get(returnJobIndex);
        if (!inventoryStackMatches(client.player, activeJob.stagingSlot,
                activeJob.key, activeJob.stagedCount)) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        clickPlayerInventorySlot(client, activeJob.stagingSlot, 0, State.BATCH_WAIT_PICK_RETURN);
    }

    private void batchPlaceReturn(Minecraft client) {
        LocalPlayer player = client.player;
        if (!validOpenBoxMenu(player.containerMenu)
                || !player.containerMenu.getSlot(activeJob.sourceBoxSlot).getItem().isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        clickContainerSlot(client, activeJob.sourceBoxSlot, 0, State.BATCH_WAIT_PLACE_RETURN);
        returnJobIndex++;
    }

    private void completeBatch() {
        transferIndex = batch.endTransferIndex();
        batch = null;
        state = State.BEGIN_OPERATION;
    }

    private void beginOperation() {
        if (transferIndex >= transfers.size()) {
            state = State.BEGIN_SWAP_CYCLE;
            return;
        }
        transfer = transfers.get(transferIndex);
        state = State.OPEN_SOURCE;
    }

    private void openBox(Minecraft client, int inventorySlot, State waitState) {
        LocalPlayer player = client.player;
        if (player == null || player.containerMenu != player.inventoryMenu
                || !player.inventoryMenu.getCarried().isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        ItemStack box = player.getInventory().getItem(inventorySlot);
        if (!ShulkerScanner.isVanillaShulkerBox(box) || box.getCount() != 1
                || ShulkerScanner.isNamedOrSpecial(box)) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        waitRevision = PacketSyncTracker.revision();
        deadline = age + SYNC_TIMEOUT_TICKS;
        int menuSlot = player.inventoryMenu.findSlot(player.getInventory(), inventorySlot).orElse(-1);
        expectingHiddenContainerScreen = backgroundMode;
        if (menuSlot < 0 || !QuickShulkerBridge.open(box, menuSlot)) {
            expectingHiddenContainerScreen = false;
            abort(client, Component.translatable("message.shulkerbox_sort.quickshulker_unavailable"));
            return;
        }
        state = waitState;
    }

    private void waitForOpenContainer(Minecraft client, State next) {
        LocalPlayer player = client.player;
        if (player == null) {
            state = State.STOPPED;
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != player.inventoryMenu && menu.slots.size() >= ShulkerScanner.SHULKER_SLOTS + 36
                && PacketSyncTracker.contentAdvancedFor(waitRevision, menu.containerId)) {
            openContainerId = menu.containerId;
            expectingHiddenContainerScreen = false;
            state = next;
            return;
        }
        if (age >= deadline) {
            ShulkerBoxSortClient.LOGGER.warn(
                    "Click timed out: state={}, next={}, expectedContainer={}, actualContainer={}, "
                            + "expectedStateId={}, actualStateId={}, cursor={}",
                    state, next, waitContainerId, player.containerMenu.containerId,
                    waitStateId, player.containerMenu.getStateId(),
                    player.containerMenu.getCarried());
            abort(client, Component.translatable("message.shulkerbox_sort.sync_timeout"));
        }
    }

    private void pickSource(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !validOpenBoxMenu(player.containerMenu)) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        ItemStack source = player.containerMenu.getSlot(transfer.sourceBoxSlot()).getItem();
        if (!transfer.key().matches(source) || source.getCount() < transfer.amount()) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        if (!player.getInventory().getItem(stagingInventorySlot).isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.staging_occupied"));
            return;
        }
        pickedCount = source.getCount();
        clickContainerSlot(client, transfer.sourceBoxSlot(), 0, State.WAIT_PICK_SOURCE);
    }

    private void pickStagedForTarget(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !validOpenBoxMenu(player.containerMenu)
                || !inventoryStackMatches(player, stagingInventorySlot, transfer.key(), pickedCount)) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        clickPlayerInventorySlot(client, stagingInventorySlot, 0, State.WAIT_PICK_STAGE);
    }

    private void placeTarget(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !validOpenBoxMenu(player.containerMenu)) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        ItemStack target = player.containerMenu.getSlot(transfer.targetBoxSlot()).getItem();
        if (!target.isEmpty() && !transfer.key().matches(target)) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        targetBeforeCount = target.isEmpty() ? 0 : target.getCount();
        if (targetBeforeCount + transfer.amount() > transfer.key().template().getMaxStackSize()) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        clickContainerSlot(client, transfer.targetBoxSlot(), 0, State.WAIT_PLACE_TARGET);
    }

    private void placeReturnSource(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !validOpenBoxMenu(player.containerMenu)
                || !player.containerMenu.getSlot(transfer.sourceBoxSlot()).getItem().isEmpty()) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        clickContainerSlot(client, transfer.sourceBoxSlot(), 0, State.WAIT_PLACE_RETURN_SOURCE);
    }

    private void closeContainer(LocalPlayer player, State waitState) {
        if (!player.containerMenu.getCarried().isEmpty()) {
            abort(Minecraft.getInstance(), Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        player.closeContainer();
        hiddenContainerScreen = null;
        openContainerId = Integer.MIN_VALUE;
        deadline = age + CLOSE_TIMEOUT_TICKS;
        state = waitState;
    }

    private void waitForClosed(LocalPlayer player, State next) {
        if (player.containerMenu == player.inventoryMenu
                && player.inventoryMenu.getCarried().isEmpty()) {
            // closeContainer() restores inventoryMenu synchronously and the
            // close/open packets retain network order. Start the next guarded
            // open immediately instead of burning one whole client tick.
            if (next == State.BEGIN_OPERATION
                    && (state == State.WAIT_TARGET_CLOSE || state == State.WAIT_RETURN_SOURCE_CLOSE)) {
                transferIndex++;
            }
            state = next;
            return;
        }
        if (age >= deadline) {
            abort(Minecraft.getInstance(), Component.translatable("message.shulkerbox_sort.sync_timeout"));
        }
    }

    private void prepareEmptyMerges(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) {
            abort(Minecraft.getInstance(), Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        emptyMerges = EmptyBoxMergePlanner.plan(player.getInventory());
        emptyMergeIndex = 0;
        state = State.BEGIN_EMPTY_MERGE;
    }

    private void beginEmptyMerge(LocalPlayer player) {
        if (emptyMergeIndex >= emptyMerges.size()) {
            finish(player);
            return;
        }
        emptyMerge = emptyMerges.get(emptyMergeIndex);
        state = State.PICK_EMPTY_SOURCE;
    }

    private void pickEmptySource(Minecraft client) {
        LocalPlayer player = client.player;
        ItemStack source = player.getInventory().getItem(emptyMerge.sourceInventorySlot());
        ItemStack target = player.getInventory().getItem(emptyMerge.targetInventorySlot());
        if (!emptyMerge.key().matches(source) || !emptyMerge.key().matches(target)
                || source.getCount() < emptyMerge.amount()
                || target.getCount() + emptyMerge.amount() > 64) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        emptyMergeCount = source.getCount();
        emptyTargetBeforeCount = target.getCount();
        clickInventoryMenuSlot(client, emptyMerge.sourceInventorySlot(), 0,
                State.WAIT_PICK_EMPTY_SOURCE);
    }

    private void placeEmptyTarget(Minecraft client) {
        clickInventoryMenuSlot(client, emptyMerge.targetInventorySlot(), 0,
                State.WAIT_PLACE_EMPTY_TARGET);
        emptyMergeIndex++;
    }

    private void returnEmptyRemainder(Minecraft client) {
        clickInventoryMenuSlot(client, emptyMerge.sourceInventorySlot(), 0,
                State.WAIT_RETURN_EMPTY_REMAINDER);
    }

    private void finish(LocalPlayer player) {
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            abort(Minecraft.getInstance(), Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        player.sendOverlayMessage(Component.translatable(
                "message.shulkerbox_sort.completed", emptiedBoxes, remainingBoxes, crossBoxMoves));
        state = State.STOPPED;
    }

    private void clickContainerSlot(Minecraft client, int menuSlot, int button, State waitState) {
        sendClick(client, client.player.containerMenu, menuSlot, button, waitState);
    }

    private void clickPlayerInventorySlot(Minecraft client, int inventorySlot, int button, State waitState) {
        LocalPlayer player = client.player;
        int menuSlot = player.containerMenu.findSlot(player.getInventory(), inventorySlot).orElse(-1);
        if (menuSlot < 0) {
            abort(client, Component.translatable("message.shulkerbox_sort.inventory_changed"));
            return;
        }
        sendClick(client, player.containerMenu, menuSlot, button, waitState);
    }

    private void clickInventoryMenuSlot(Minecraft client, int inventorySlot, int button, State waitState) {
        LocalPlayer player = client.player;
        if (player.containerMenu != player.inventoryMenu) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        clickPlayerInventorySlot(client, inventorySlot, button, waitState);
    }

    private void sendInventorySwap(Minecraft client, int inventorySlot, int hotbarSlot, State waitState) {
        LocalPlayer player = client.player;
        if (player == null || player.containerMenu != player.inventoryMenu) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        int menuSlot = player.inventoryMenu.findSlot(player.getInventory(), inventorySlot).orElse(-1);
        sendSwap(client, player.inventoryMenu, menuSlot, hotbarSlot, waitState);
    }

    private void sendSwap(Minecraft client, AbstractContainerMenu menu, int menuSlot,
                          int hotbarSlot, State waitState) {
        sendInput(client, menu, menuSlot, hotbarSlot, ContainerInput.SWAP, waitState);
    }

    private void sendClick(Minecraft client, AbstractContainerMenu menu, int menuSlot, int button, State waitState) {
        sendInput(client, menu, menuSlot, button, ContainerInput.PICKUP, waitState);
    }

    private void sendInput(Minecraft client, AbstractContainerMenu menu, int menuSlot, int button,
                           ContainerInput input, State waitState) {
        if (client.gameMode == null || client.player == null || menuSlot < 0 || menuSlot >= menu.slots.size()) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        waitRevision = PacketSyncTracker.revision();
        waitContainerId = menu.containerId;
        waitStateId = menu.getStateId();
        clickSentAt = age;
        deadline = age + SYNC_TIMEOUT_TICKS;
        client.gameMode.handleContainerInput(
                menu.containerId, menuSlot, button, input, client.player);
        state = waitState;
    }

    private void waitForClick(Minecraft client, BooleanSupplier validation, State next) {
        LocalPlayer player = client.player;
        if (player == null || player.containerMenu.containerId != waitContainerId) {
            abort(client, Component.translatable("message.shulkerbox_sort.safe_abort"));
            return;
        }
        if (clickSettled(player, validation)) {
            state = next;
            return;
        }
        abortIfClickTimedOut(client, player, next);
    }

    private boolean clickSettled(LocalPlayer player, BooleanSupplier validation) {
        if (player.containerMenu.containerId != waitContainerId || !validation.getAsBoolean()) {
            return false;
        }
        boolean serverConfirmed = PacketSyncTracker.confirmedAfter(
                waitRevision, waitContainerId, waitStateId, player.containerMenu.getStateId());
        return serverConfirmed || age >= clickSentAt + PREDICTION_SETTLE_TICKS;
    }

    private void abortIfClickTimedOut(Minecraft client, LocalPlayer player, State next) {
        if (age < deadline) {
            return;
        }
        ShulkerBoxSortClient.LOGGER.warn(
                "Click timed out: state={}, next={}, expectedContainer={}, actualContainer={}, "
                        + "expectedStateId={}, actualStateId={}, cursor={}",
                state, next, waitContainerId, player.containerMenu.containerId,
                waitStateId, player.containerMenu.getStateId(), player.containerMenu.getCarried());
        abort(client, Component.translatable("message.shulkerbox_sort.sync_timeout"));
    }

    private void abort(Minecraft client, Component reason) {
        if (state == State.STOPPED || state == State.ABORT_RECOVERY_WAIT
                || state == State.ABORT_WAIT_INVENTORY || state == State.ABORT_RESTORE_HOTBAR
                || state == State.ABORT_WAIT_HOTBAR_RESTORE) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null) {
            state = State.STOPPED;
            return;
        }
        abortMessage = reason;
        expectingHiddenContainerScreen = false;
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            continueAbortWithEmptyCursor(player);
            return;
        }

        int recoverySlot = findRecoveryMenuSlot(player, carried);
        if (recoverySlot < 0 || client.gameMode == null) {
            revealHiddenContainerForRecovery();
            player.sendOverlayMessage(Component.translatable(
                    "message.shulkerbox_sort.cursor_recovery_failed"));
            state = State.STOPPED;
            return;
        }
        sendClick(client, player.containerMenu, recoverySlot, 0, State.ABORT_RECOVERY_WAIT);
    }

    private void finishAbortRecovery(LocalPlayer player) {
        boolean serverConfirmed = PacketSyncTracker.confirmedAfter(
                waitRevision, waitContainerId, waitStateId, player.containerMenu.getStateId());
        if ((serverConfirmed || age >= clickSentAt + 4)
                && player.containerMenu.getCarried().isEmpty()) {
            continueAbortWithEmptyCursor(player);
            return;
        }
        if (age >= deadline) {
            revealHiddenContainerForRecovery();
            player.sendOverlayMessage(Component.translatable(
                    "message.shulkerbox_sort.cursor_recovery_failed"));
            state = State.STOPPED;
        }
    }

    private void continueAbortWithEmptyCursor(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        boolean borrowOutstanding = borrowedDestinationSlot >= 0
                && !inventory.getItem(borrowedDestinationSlot).isEmpty();
        boolean liveWorkspaceToken = !inventory.getItem(workspaceHotbarSlot).isEmpty()
                && (borrowedDestinationSlot < 0 || borrowOutstanding);
        if (liveWorkspaceToken) {
            revealHiddenContainerForRecovery();
            player.sendOverlayMessage(Component.translatable(
                    "message.shulkerbox_sort.hotbar_restore_failed"));
            // The hotbar contains a live permutation token. Keep the current
            // box open so the player can see and manually reverse the swap.
            state = State.STOPPED;
            return;
        }
        if (borrowOutstanding) {
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
                deadline = age + CLOSE_TIMEOUT_TICKS;
                state = State.ABORT_WAIT_INVENTORY;
            } else {
                state = State.ABORT_RESTORE_HOTBAR;
            }
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        player.sendOverlayMessage(abortMessage);
        state = State.STOPPED;
    }

    private void waitForAbortInventory(LocalPlayer player) {
        if (player.containerMenu == player.inventoryMenu && player.inventoryMenu.getCarried().isEmpty()) {
            state = State.ABORT_RESTORE_HOTBAR;
        } else if (age >= deadline) {
            player.sendOverlayMessage(Component.translatable(
                    "message.shulkerbox_sort.hotbar_restore_failed"));
            state = State.STOPPED;
        }
    }

    private void restoreHotbarAfterAbort(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || player.containerMenu != player.inventoryMenu
                || borrowedDestinationSlot < 0
                || !player.getInventory().getItem(workspaceHotbarSlot).isEmpty()
                || !borrowedStackMatches(player.getInventory().getItem(borrowedDestinationSlot))) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable(
                        "message.shulkerbox_sort.hotbar_restore_failed"));
            }
            state = State.STOPPED;
            return;
        }
        sendInventorySwap(client, borrowedDestinationSlot, workspaceHotbarSlot,
                State.ABORT_WAIT_HOTBAR_RESTORE);
    }

    private void waitForAbortHotbarRestore(LocalPlayer player) {
        BooleanSupplier validation = () -> player.getInventory().getItem(borrowedDestinationSlot).isEmpty()
                && !player.getInventory().getItem(workspaceHotbarSlot).isEmpty()
                && player.inventoryMenu.getCarried().isEmpty();
        if (clickSettled(player, validation)) {
            player.sendOverlayMessage(abortMessage);
            state = State.STOPPED;
        } else if (age >= deadline) {
            player.sendOverlayMessage(Component.translatable(
                    "message.shulkerbox_sort.hotbar_restore_failed"));
            state = State.STOPPED;
        }
    }

    private void revealHiddenContainerForRecovery() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.containerMenu != client.player.inventoryMenu
                && hiddenContainerScreen != null) {
            client.setScreen(hiddenContainerScreen);
        }
    }

    private int findRecoveryMenuSlot(LocalPlayer player, ItemStack carried) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < ShulkerScanner.PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack target = inventory.getItem(i);
            if (target.isEmpty() || ItemStack.isSameItemSameComponents(target, carried)
                    && target.getCount() + carried.getCount() <= target.getMaxStackSize()) {
                int menuSlot = player.containerMenu.findSlot(inventory, i).orElse(-1);
                if (menuSlot >= 0) {
                    return menuSlot;
                }
            }
        }
        return -1;
    }

    private boolean validOpenBoxMenu(AbstractContainerMenu menu) {
        return menu.containerId == openContainerId
                && menu.slots.size() >= ShulkerScanner.SHULKER_SLOTS + 36;
    }

    private static boolean carriedMatches(LocalPlayer player, ItemStackKey key, int count) {
        ItemStack carried = player.containerMenu.getCarried();
        return count == 0 ? carried.isEmpty() : key.matches(carried) && carried.getCount() == count;
    }

    private static boolean inventoryStackMatches(LocalPlayer player, int inventorySlot,
                                                  ItemStackKey key, int count) {
        ItemStack stack = player.getInventory().getItem(inventorySlot);
        return count == 0 ? stack.isEmpty() : key.matches(stack) && stack.getCount() == count;
    }

    private static boolean containerStackMatches(AbstractContainerMenu menu, int slot,
                                                  ItemStackKey key, int count) {
        ItemStack stack = menu.getSlot(slot).getItem();
        return count == 0 ? stack.isEmpty() : key.matches(stack) && stack.getCount() == count;
    }

    private static boolean stackMatches(ItemStack actual, PackingPlanner.Stack<ItemStackKey> expected) {
        return expected == null ? actual.isEmpty()
                : expected.key().matches(actual) && actual.getCount() == expected.count();
    }

    private static int occupiedBoxSlots(AbstractContainerMenu menu) {
        int occupied = 0;
        for (int slot = 0; slot < ShulkerScanner.SHULKER_SLOTS; slot++) {
            if (!menu.getSlot(slot).getItem().isEmpty()) {
                occupied++;
            }
        }
        return occupied;
    }

    private static List<ItemStack> copyBoxLayout(AbstractContainerMenu menu) {
        List<ItemStack> result = new ArrayList<>(ShulkerScanner.SHULKER_SLOTS);
        for (int slot = 0; slot < ShulkerScanner.SHULKER_SLOTS; slot++) {
            result.add(menu.getSlot(slot).getItem().copy());
        }
        return List.copyOf(result);
    }

    private static boolean boxLayoutMatches(AbstractContainerMenu menu, List<ItemStack> expected) {
        if (expected.size() != ShulkerScanner.SHULKER_SLOTS) {
            return false;
        }
        for (int slot = 0; slot < expected.size(); slot++) {
            ItemStack actual = menu.getSlot(slot).getItem();
            ItemStack before = expected.get(slot);
            if (actual.getCount() != before.getCount()
                    || !ItemStack.isSameItemSameComponents(actual, before)) {
                return false;
            }
        }
        return true;
    }

    private boolean borrowedStackMatches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (borrowedEligibleShulker) {
            return ShulkerScanner.isVanillaShulkerBox(stack)
                    && !ShulkerScanner.isNamedOrSpecial(stack) && stack.getCount() == 1;
        }
        return borrowedOriginalKey != null && borrowedOriginalKey.matches(stack)
                && stack.getCount() == borrowedOriginalCount;
    }

    private static void message(LocalPlayer player, String translationKey) {
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(translationKey));
        }
    }
}
