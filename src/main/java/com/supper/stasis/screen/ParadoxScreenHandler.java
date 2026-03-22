package com.supper.stasis.screen;

import com.supper.stasis.Stasis;
import com.supper.stasis.item.ParadoxLapseMode;
import com.supper.stasis.item.ParadoxStateComponent;
import com.supper.stasis.item.ParadoxStateHelper;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

public class ParadoxScreenHandler extends ScreenHandler {
    public static final int BUTTON_SELECT_10 = 0;
    public static final int BUTTON_SELECT_20 = 1;
    public static final int BUTTON_SELECT_30 = 2;
    public static final int BUTTON_APPLY = 3;

    private static final int GRIT_SLOT_COUNT = 4;
    private static final int UPGRADE_SLOT_COUNT = 4;
    private static final int PANEL_SLOT_COUNT = GRIT_SLOT_COUNT + UPGRADE_SLOT_COUNT;
    private static final int VIRTUAL_PLAYER_SLOT_COUNT = 36;

    private static final int PROPERTY_TIER = 0;
    private static final int PROPERTY_SELECTED_MODE = 1;
    private static final int PROPERTY_CUSTOM_SECONDS = 2;

    private static final int UPGRADE_LEFT_X = 39;
    private static final int UPGRADE_RIGHT_X = 115;
    private static final int UPGRADE_TOP_Y = 28;
    private static final int UPGRADE_BOTTOM_Y = 104;
    private static final int GRIT_TOP_X = 77;
    private static final int GRIT_LEFT_X = 44;
    private static final int GRIT_RIGHT_X = 110;
    private static final int GRIT_TOP_Y = 33;
    private static final int GRIT_MIDDLE_Y = 66;
    private static final int GRIT_BOTTOM_Y = 99;
    private static final int PLAYER_GRID_START_X = 9;
    private static final int PLAYER_GRID_START_Y = 156;
    private static final int PLAYER_GRID_X_SPACING = 17;
    private static final int PLAYER_GRID_Y_SPACING = 16;
    private static final int HOTBAR_Y = 212;

    private final SimpleInventory panelInventory;
    private final SimpleInventory virtualPlayerInventory;
    private final PropertyDelegate properties;
    private final Hand openHand;
    private final int openSlotIndex;
    private final UUID expectedItemUuid;
    private boolean applied;

    public ParadoxScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, Hand.MAIN_HAND, -1, null);
    }

    public ParadoxScreenHandler(int syncId, PlayerInventory playerInventory, Hand hand, int openSlotIndex, UUID expectedItemUuid) {
        super(Stasis.PARADOX_SCREEN_HANDLER_TYPE, syncId);
        this.panelInventory = new SimpleInventory(PANEL_SLOT_COUNT);
        this.virtualPlayerInventory = new SimpleInventory(VIRTUAL_PLAYER_SLOT_COUNT);
        this.properties = new ArrayPropertyDelegate(3);
        this.openHand = hand;
        this.openSlotIndex = openSlotIndex;
        this.expectedItemUuid = expectedItemUuid;

        initializeFromPlayer(playerInventory);
        addPanelSlots();
        addVirtualInventorySlots();
        addProperties(this.properties);
    }

    private void initializeFromPlayer(PlayerInventory playerInventory) {
        ItemStack paradoxStack = getOpenParadoxStack(playerInventory.player);
        if (!paradoxStack.isEmpty() && paradoxStack.isOf(Stasis.PARADOX)) {
            ParadoxStateComponent state = ParadoxStateHelper.getState(paradoxStack);
            this.properties.set(PROPERTY_TIER, state.tier());
            this.properties.set(PROPERTY_SELECTED_MODE, state.selectedLapseMode().ordinal());
            this.properties.set(PROPERTY_CUSTOM_SECONDS, state.customLapseSeconds());
            int remainingGrits = state.gritCount();
            int perSlotLimit = ParadoxStateHelper.getMaxGritsPerSlot(state.tier());
            for (int slot = 0; slot < GRIT_SLOT_COUNT && remainingGrits > 0; slot++) {
                int stackSize = Math.min(perSlotLimit, remainingGrits);
                this.panelInventory.setStack(slot, new ItemStack(Stasis.TEMPORAL_GRIT, stackSize));
                remainingGrits -= stackSize;
            }
        } else {
            this.properties.set(PROPERTY_TIER, ParadoxStateHelper.TIER_ONE);
            this.properties.set(PROPERTY_SELECTED_MODE, ParadoxLapseMode.PRESET_10.ordinal());
            this.properties.set(PROPERTY_CUSTOM_SECONDS, 10);
        }

        for (int virtualSlot = 0; virtualSlot < 27; virtualSlot++) {
            this.virtualPlayerInventory.setStack(virtualSlot, playerInventory.getStack(virtualSlot + 9).copy());
        }
        for (int virtualSlot = 27; virtualSlot < VIRTUAL_PLAYER_SLOT_COUNT; virtualSlot++) {
            this.virtualPlayerInventory.setStack(virtualSlot, playerInventory.getStack(virtualSlot - 27).copy());
        }
    }

    private void addPanelSlots() {
        addSlot(new GritSlot(this.panelInventory, 0, GRIT_TOP_X, GRIT_TOP_Y));
        addSlot(new GritSlot(this.panelInventory, 1, GRIT_RIGHT_X, GRIT_MIDDLE_Y));
        addSlot(new GritSlot(this.panelInventory, 2, GRIT_TOP_X, GRIT_BOTTOM_Y));
        addSlot(new GritSlot(this.panelInventory, 3, GRIT_LEFT_X, GRIT_MIDDLE_Y));

        addSlot(new UpgradeSlot(this.panelInventory, 4, UPGRADE_LEFT_X, UPGRADE_TOP_Y));
        addSlot(new UpgradeSlot(this.panelInventory, 5, UPGRADE_RIGHT_X, UPGRADE_TOP_Y));
        addSlot(new UpgradeSlot(this.panelInventory, 6, UPGRADE_LEFT_X, UPGRADE_BOTTOM_Y));
        addSlot(new UpgradeSlot(this.panelInventory, 7, UPGRADE_RIGHT_X, UPGRADE_BOTTOM_Y));
    }

    private void addVirtualInventorySlots() {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int virtualIndex = column + row * 9;
                int actualInventoryIndex = virtualIndex + 9;
                addSlot(new VirtualPlayerSlot(
                        this.virtualPlayerInventory,
                        virtualIndex,
                        PLAYER_GRID_START_X + column * PLAYER_GRID_X_SPACING,
                        PLAYER_GRID_START_Y + row * PLAYER_GRID_Y_SPACING,
                        actualInventoryIndex
                ));
            }
        }
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            int virtualIndex = 27 + hotbarSlot;
            addSlot(new VirtualPlayerSlot(
                    this.virtualPlayerInventory,
                    virtualIndex,
                    PLAYER_GRID_START_X + hotbarSlot * PLAYER_GRID_X_SPACING,
                    HOTBAR_Y,
                    hotbarSlot
            ));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        ItemStack stack = getOpenParadoxStack(player);
        if (stack.isEmpty() || !stack.isOf(Stasis.PARADOX)) {
            return false;
        }
        if (this.expectedItemUuid == null) {
            return true;
        }
        Optional<UUID> actualUuid = ParadoxStateHelper.getState(stack).itemUuid();
        return actualUuid.isPresent() && actualUuid.get().equals(this.expectedItemUuid);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasStack()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getStack();
        moved = stack.copy();
        if (index < PANEL_SLOT_COUNT) {
            if (!insertItem(stack, PANEL_SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertItem(stack, 0, PANEL_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return moved;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        return switch (id) {
            case BUTTON_SELECT_10 -> selectMode(ParadoxLapseMode.PRESET_10);
            case BUTTON_SELECT_20 -> selectMode(ParadoxLapseMode.PRESET_20);
            case BUTTON_SELECT_30 -> selectMode(ParadoxLapseMode.PRESET_30);
            case BUTTON_APPLY -> applyChanges(player);
            default -> false;
        };
    }

    @Override
    public void onClosed(PlayerEntity player) {
        if (!this.applied) {
            this.setCursorStack(ItemStack.EMPTY);
        }
        super.onClosed(player);
    }

    public void setPendingCustomLapseSeconds(int seconds) {
        if (getCurrentTier() < ParadoxStateHelper.TIER_THREE) {
            return;
        }
        int clampedSeconds = Math.max(ParadoxStateHelper.MIN_CUSTOM_SECONDS, Math.min(ParadoxStateHelper.MAX_CUSTOM_SECONDS, seconds));
        this.properties.set(PROPERTY_CUSTOM_SECONDS, clampedSeconds);
        this.properties.set(PROPERTY_SELECTED_MODE, ParadoxLapseMode.CUSTOM.ordinal());
        sendContentUpdates();
    }

    public int getCurrentTier() {
        return this.properties.get(PROPERTY_TIER);
    }

    public ParadoxLapseMode getSelectedMode() {
        return ParadoxLapseMode.fromId(this.properties.get(PROPERTY_SELECTED_MODE));
    }

    public int getCustomSeconds() {
        return this.properties.get(PROPERTY_CUSTOM_SECONDS);
    }

    private boolean selectMode(ParadoxLapseMode mode) {
        ParadoxStateComponent currentState = getCurrentState();
        if (!ParadoxStateHelper.isModeUnlocked(currentState, mode)) {
            return false;
        }
        this.properties.set(PROPERTY_SELECTED_MODE, mode.ordinal());
        sendContentUpdates();
        return true;
    }

    private boolean applyChanges(PlayerEntity player) {
        if (!this.getCursorStack().isEmpty()) {
            return false;
        }

        ItemStack paradoxStack = getOpenParadoxStack(player);
        if (paradoxStack.isEmpty() || !paradoxStack.isOf(Stasis.PARADOX)) {
            return false;
        }

        ParadoxStateComponent state = ParadoxStateHelper.getState(paradoxStack);
        if (!ParadoxStateHelper.isModeUnlocked(state, getSelectedMode())) {
            return false;
        }

        int targetTier = determineTargetTier(state);
        if (targetTier < 0) {
            return false;
        }

        PlayerInventory playerInventory = player.getInventory();
        for (int virtualSlot = 0; virtualSlot < VIRTUAL_PLAYER_SLOT_COUNT; virtualSlot++) {
            int actualIndex = getActualInventoryIndex(virtualSlot);
            if (this.openHand == Hand.MAIN_HAND && actualIndex == this.openSlotIndex) {
                continue;
            }
            playerInventory.setStack(actualIndex, this.virtualPlayerInventory.getStack(virtualSlot).copy());
        }

        int gritCount = 0;
        for (int slot = 0; slot < GRIT_SLOT_COUNT; slot++) {
            ItemStack gritStack = this.panelInventory.getStack(slot);
            if (!gritStack.isEmpty()) {
                gritCount += gritStack.getCount();
            }
        }

        ParadoxStateComponent updated = state
                .withTier(targetTier)
                .withGritCount(gritCount)
                .withSelectedLapseMode(getSelectedMode())
                .withCustomLapseSeconds(getCustomSeconds());
        ParadoxStateHelper.setState(paradoxStack, updated);

        for (int slot = 0; slot < PANEL_SLOT_COUNT; slot++) {
            this.panelInventory.setStack(slot, ItemStack.EMPTY);
        }

        playerInventory.markDirty();
        this.applied = true;
        this.setCursorStack(ItemStack.EMPTY);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.closeHandledScreen();
        }
        player.playerScreenHandler.syncState();
        return true;
    }

    private int determineTargetTier(ParadoxStateComponent state) {
        boolean hasUpgradeItems = false;
        for (int slot = GRIT_SLOT_COUNT; slot < PANEL_SLOT_COUNT; slot++) {
            if (!this.panelInventory.getStack(slot).isEmpty()) {
                hasUpgradeItems = true;
                break;
            }
        }

        if (!hasUpgradeItems) {
            return state.tier();
        }

        if (state.tier() == ParadoxStateHelper.TIER_ONE && allUpgradeSlotsContain(Stasis.REINFORCED_DIAMOND)) {
            return ParadoxStateHelper.TIER_TWO;
        }
        if (state.tier() == ParadoxStateHelper.TIER_TWO && allUpgradeSlotsContain(Stasis.REINFORCED_NETHERITE)) {
            return ParadoxStateHelper.TIER_THREE;
        }
        return -1;
    }

    private boolean allUpgradeSlotsContain(net.minecraft.item.Item item) {
        for (int slot = GRIT_SLOT_COUNT; slot < PANEL_SLOT_COUNT; slot++) {
            ItemStack stack = this.panelInventory.getStack(slot);
            if (!stack.isOf(item) || stack.getCount() != 1) {
                return false;
            }
        }
        return true;
    }

    private ParadoxStateComponent getCurrentState() {
        return new ParadoxStateComponent(
                getCurrentTier(),
                0,
                getSelectedMode(),
                getCustomSeconds(),
                Optional.ofNullable(this.expectedItemUuid)
        );
    }

    private ItemStack getOpenParadoxStack(PlayerEntity player) {
        ItemStack stack = player.getStackInHand(this.openHand);
        if (!stack.isEmpty() && stack.isOf(Stasis.PARADOX)) {
            return stack;
        }
        if (this.openHand == Hand.MAIN_HAND && this.openSlotIndex >= 0 && this.openSlotIndex < 9) {
            ItemStack selected = player.getInventory().getStack(this.openSlotIndex);
            if (selected.isOf(Stasis.PARADOX)) {
                return selected;
            }
        }
        return ItemStack.EMPTY;
    }

    private int getActualInventoryIndex(int virtualSlot) {
        return virtualSlot < 27 ? virtualSlot + 9 : virtualSlot - 27;
    }

    private final class GritSlot extends Slot {
        private GritSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.isOf(Stasis.TEMPORAL_GRIT);
        }

        @Override
        public int getMaxItemCount() {
            return ParadoxStateHelper.getMaxGritsPerSlot(getCurrentTier());
        }

        @Override
        public int getMaxItemCount(ItemStack stack) {
            return ParadoxStateHelper.getMaxGritsPerSlot(getCurrentTier());
        }
    }

    private final class UpgradeSlot extends Slot {
        private UpgradeSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            int tier = getCurrentTier();
            if (tier == ParadoxStateHelper.TIER_ONE) {
                return stack.isOf(Stasis.REINFORCED_DIAMOND);
            }
            if (tier == ParadoxStateHelper.TIER_TWO) {
                return stack.isOf(Stasis.REINFORCED_NETHERITE);
            }
            return false;
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    private final class VirtualPlayerSlot extends Slot {
        private final int actualInventoryIndex;

        private VirtualPlayerSlot(Inventory inventory, int index, int x, int y, int actualInventoryIndex) {
            super(inventory, index, x, y);
            this.actualInventoryIndex = actualInventoryIndex;
        }

        @Override
        public boolean canTakeItems(PlayerEntity playerEntity) {
            return !isLocked();
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return !isLocked();
        }

        private boolean isLocked() {
            return openHand == Hand.MAIN_HAND && actualInventoryIndex == openSlotIndex;
        }
    }
}
