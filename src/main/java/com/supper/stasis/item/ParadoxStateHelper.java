package com.supper.stasis.item;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

public final class ParadoxStateHelper {
    public static final int MAX_GRITS_TIER_ONE = 4;
    public static final int MAX_GRITS_TIER_TWO = 12;
    public static final int MAX_GRITS_TIER_THREE = 40;
    public static final int MIN_CUSTOM_SECONDS = 1;
    public static final int MAX_CUSTOM_SECONDS = 3599;
    public static final int TIER_ONE = 1;
    public static final int TIER_TWO = 2;
    public static final int TIER_THREE = 3;
    private static final int MODEL_ASTRAL = 1;
    private static final int MODEL_VOID = 2;
    private static final String STATE_SUB_NBT = "stasis_paradox_state";
    private static final String KEY_TIER = "tier";
    private static final String KEY_GRIT_COUNT = "grit_count";
    private static final String KEY_SELECTED_LAPSE_MODE = "selected_lapse_mode";
    private static final String KEY_CUSTOM_LAPSE_SECONDS = "custom_lapse_seconds";
    private static final String KEY_ITEM_UUID = "item_uuid";
    private static final String KEY_CUSTOM_MODEL_DATA = "CustomModelData";

    private ParadoxStateHelper() {
    }

    public static ParadoxStateComponent getState(ItemStack stack) {
        ParadoxStateComponent current = readState(stack);
        ParadoxStateComponent normalized = normalize(current);
        if (normalized.itemUuid().isEmpty()) {
            normalized = normalized.withItemUuid(UUID.randomUUID());
        }
        if (!normalized.equals(current) || !hasExpectedVisualState(stack, normalized)) {
            setState(stack, normalized);
            return normalized;
        }
        return normalized;
    }

    public static void setState(ItemStack stack, ParadoxStateComponent state) {
        ParadoxStateComponent normalized = normalize(state);
        NbtCompound stateNbt = stack.getOrCreateSubNbt(STATE_SUB_NBT);
        stateNbt.putInt(KEY_TIER, normalized.tier());
        stateNbt.putInt(KEY_GRIT_COUNT, normalized.gritCount());
        stateNbt.putString(KEY_SELECTED_LAPSE_MODE, normalized.selectedLapseMode().name());
        stateNbt.putInt(KEY_CUSTOM_LAPSE_SECONDS, normalized.customLapseSeconds());
        if (normalized.itemUuid().isPresent()) {
            stateNbt.putString(KEY_ITEM_UUID, normalized.itemUuid().get().toString());
        } else {
            stateNbt.remove(KEY_ITEM_UUID);
        }
        applyVisualComponents(stack, normalized);
    }

    public static boolean consumeUse(ItemStack stack) {
        ParadoxStateComponent state = getState(stack);
        if (state.gritCount() <= 0) {
            return false;
        }
        setState(stack, state.withGritCount(state.gritCount() - 1));
        return true;
    }

    public static boolean isUsable(ItemStack stack) {
        return getState(stack).gritCount() > 0;
    }

    public static int getMaxGrits(ParadoxStateComponent state) {
        return getMaxGritsForTier(state.tier());
    }

    public static int getMaxGrits(ItemStack stack) {
        return getMaxGrits(getState(stack));
    }

    public static int getMaxGritsForTier(int tier) {
        return switch (tier) {
            case TIER_TWO -> MAX_GRITS_TIER_TWO;
            case TIER_THREE -> MAX_GRITS_TIER_THREE;
            default -> MAX_GRITS_TIER_ONE;
        };
    }

    public static int getMaxGritsPerSlot(int tier) {
        return switch (tier) {
            case TIER_TWO -> 3;
            case TIER_THREE -> 10;
            default -> 1;
        };
    }

    public static int getSelectedLapseSeconds(ItemStack stack) {
        return getSelectedLapseSeconds(getState(stack));
    }

    public static int getSelectedLapseSeconds(ParadoxStateComponent state) {
        return switch (state.selectedLapseMode()) {
            case PRESET_10 -> 10;
            case PRESET_20 -> 20;
            case PRESET_30 -> 30;
            case CUSTOM -> clampSeconds(state.customLapseSeconds());
        };
    }

    public static int getWarningTicks(ParadoxStateComponent state) {
        return switch (state.selectedLapseMode()) {
            case PRESET_10 -> 5 * 20;
            case PRESET_20 -> 10 * 20;
            case PRESET_30 -> 15 * 20;
            case CUSTOM -> 5 * 20;
        };
    }

    public static int getMaxUnlockedTier(ParadoxStateComponent state) {
        return Math.max(TIER_ONE, Math.min(TIER_THREE, state.tier()));
    }

    public static boolean isModeUnlocked(ParadoxStateComponent state, ParadoxLapseMode mode) {
        return switch (mode) {
            case PRESET_10 -> true;
            case PRESET_20 -> state.tier() >= TIER_TWO;
            case PRESET_30, CUSTOM -> state.tier() >= TIER_THREE;
        };
    }

    public static String getTierTitle(ParadoxStateComponent state) {
        return switch (state.tier()) {
            case TIER_TWO -> "Astral";
            case TIER_THREE -> "Void";
            default -> "Dawn";
        };
    }

    public static String getLapseLabel(ParadoxStateComponent state) {
        return switch (state.selectedLapseMode()) {
            case PRESET_10 -> "10s";
            case PRESET_20 -> "20s";
            case PRESET_30 -> "30s";
            case CUSTOM -> formatSeconds(getSelectedLapseSeconds(state));
        };
    }

    public static String formatSeconds(int totalSeconds) {
        int clamped = clampSeconds(totalSeconds);
        int minutes = clamped / 60;
        int seconds = clamped % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static ParadoxStateComponent normalize(ParadoxStateComponent state) {
        int tier = Math.max(TIER_ONE, Math.min(TIER_THREE, state.tier()));
        int gritCount = Math.max(0, Math.min(getMaxGritsForTier(tier), state.gritCount()));
        int customLapseSeconds = clampSeconds(state.customLapseSeconds());
        ParadoxLapseMode selectedMode = isModeUnlocked(new ParadoxStateComponent(tier, gritCount, state.selectedLapseMode(), customLapseSeconds, state.itemUuid()), state.selectedLapseMode())
                ? state.selectedLapseMode()
                : (tier >= TIER_TWO ? ParadoxLapseMode.PRESET_20 : ParadoxLapseMode.PRESET_10);
        return new ParadoxStateComponent(tier, gritCount, selectedMode, customLapseSeconds, state.itemUuid());
    }

    private static int clampSeconds(int seconds) {
        return Math.max(MIN_CUSTOM_SECONDS, Math.min(MAX_CUSTOM_SECONDS, seconds));
    }

    private static ParadoxStateComponent readState(ItemStack stack) {
        NbtCompound stateNbt = stack.getSubNbt(STATE_SUB_NBT);
        if (stateNbt == null) {
            return ParadoxStateComponent.DEFAULT;
        }

        int tier = stateNbt.contains(KEY_TIER, NbtElement.NUMBER_TYPE) ? stateNbt.getInt(KEY_TIER) : ParadoxStateComponent.DEFAULT.tier();
        int gritCount = stateNbt.contains(KEY_GRIT_COUNT, NbtElement.NUMBER_TYPE) ? stateNbt.getInt(KEY_GRIT_COUNT) : ParadoxStateComponent.DEFAULT.gritCount();
        ParadoxLapseMode selectedLapseMode = readLapseMode(stateNbt);
        int customLapseSeconds = stateNbt.contains(KEY_CUSTOM_LAPSE_SECONDS, NbtElement.NUMBER_TYPE)
                ? stateNbt.getInt(KEY_CUSTOM_LAPSE_SECONDS)
                : ParadoxStateComponent.DEFAULT.customLapseSeconds();
        return new ParadoxStateComponent(tier, gritCount, selectedLapseMode, customLapseSeconds, readItemUuid(stateNbt));
    }

    private static ParadoxLapseMode readLapseMode(NbtCompound stateNbt) {
        if (!stateNbt.contains(KEY_SELECTED_LAPSE_MODE, NbtElement.STRING_TYPE)) {
            return ParadoxStateComponent.DEFAULT.selectedLapseMode();
        }

        try {
            return ParadoxLapseMode.valueOf(stateNbt.getString(KEY_SELECTED_LAPSE_MODE));
        } catch (IllegalArgumentException exception) {
            return ParadoxStateComponent.DEFAULT.selectedLapseMode();
        }
    }

    private static java.util.Optional<UUID> readItemUuid(NbtCompound stateNbt) {
        if (!stateNbt.contains(KEY_ITEM_UUID, NbtElement.STRING_TYPE)) {
            return java.util.Optional.empty();
        }

        try {
            return java.util.Optional.of(UUID.fromString(stateNbt.getString(KEY_ITEM_UUID)));
        } catch (IllegalArgumentException exception) {
            return java.util.Optional.empty();
        }
    }

    private static boolean hasExpectedVisualState(ItemStack stack, ParadoxStateComponent state) {
        NbtCompound nbt = stack.getNbt();
        int expectedModel = switch (state.tier()) {
            case TIER_TWO -> MODEL_ASTRAL;
            case TIER_THREE -> MODEL_VOID;
            default -> 0;
        };
        if (expectedModel == 0) {
            return nbt == null || !nbt.contains(KEY_CUSTOM_MODEL_DATA, NbtElement.NUMBER_TYPE);
        }
        return nbt != null
                && nbt.contains(KEY_CUSTOM_MODEL_DATA, NbtElement.NUMBER_TYPE)
                && nbt.getInt(KEY_CUSTOM_MODEL_DATA) == expectedModel;
    }

    private static void applyVisualComponents(ItemStack stack, ParadoxStateComponent state) {
        switch (state.tier()) {
            case TIER_TWO -> stack.getOrCreateNbt().putInt(KEY_CUSTOM_MODEL_DATA, MODEL_ASTRAL);
            case TIER_THREE -> stack.getOrCreateNbt().putInt(KEY_CUSTOM_MODEL_DATA, MODEL_VOID);
            default -> {
                NbtCompound nbt = stack.getNbt();
                if (nbt != null) {
                    nbt.remove(KEY_CUSTOM_MODEL_DATA);
                    if (nbt.isEmpty()) {
                        stack.setNbt(null);
                    }
                }
            }
        }
    }
}
