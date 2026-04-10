package com.supper.stasis.item;

public enum ParadoxLapseMode {
    PRESET_10,
    PRESET_20,
    PRESET_30,
    CUSTOM;

    public static ParadoxLapseMode fromId(int id) {
        ParadoxLapseMode[] values = values();
        if (id < 0 || id >= values.length) {
            return PRESET_10;
        }
        return values[id];
    }
}
