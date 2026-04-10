package com.supper.stasis.item;

import java.util.Optional;
import java.util.UUID;

public record ParadoxStateComponent(
        int tier,
        int gritCount,
        ParadoxLapseMode selectedLapseMode,
        int customLapseSeconds,
        Optional<UUID> itemUuid
) {
    public static final ParadoxStateComponent DEFAULT = new ParadoxStateComponent(
            1,
            4,
            ParadoxLapseMode.PRESET_10,
            10,
            Optional.empty()
    );

    public ParadoxStateComponent withTier(int newTier) {
        return new ParadoxStateComponent(newTier, gritCount, selectedLapseMode, customLapseSeconds, itemUuid);
    }

    public ParadoxStateComponent withGritCount(int newGritCount) {
        return new ParadoxStateComponent(tier, newGritCount, selectedLapseMode, customLapseSeconds, itemUuid);
    }

    public ParadoxStateComponent withSelectedLapseMode(ParadoxLapseMode newMode) {
        return new ParadoxStateComponent(tier, gritCount, newMode, customLapseSeconds, itemUuid);
    }

    public ParadoxStateComponent withCustomLapseSeconds(int newCustomLapseSeconds) {
        return new ParadoxStateComponent(tier, gritCount, selectedLapseMode, newCustomLapseSeconds, itemUuid);
    }

    public ParadoxStateComponent withItemUuid(UUID newItemUuid) {
        return new ParadoxStateComponent(tier, gritCount, selectedLapseMode, customLapseSeconds, Optional.ofNullable(newItemUuid));
    }
}
