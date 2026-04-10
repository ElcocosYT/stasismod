package com.supper.stasis.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

public record ParadoxStateComponent(
        int tier,
        int gritCount,
        ParadoxLapseMode selectedLapseMode,
        int customLapseSeconds,
        Optional<UUID> itemUuid
) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);

    public static final ParadoxStateComponent DEFAULT = new ParadoxStateComponent(
            1,
            4,
            ParadoxLapseMode.PRESET_10,
            10,
            Optional.empty()
    );

    public static final Codec<ParadoxStateComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("tier", DEFAULT.tier()).forGetter(ParadoxStateComponent::tier),
            Codec.INT.optionalFieldOf("grit_count", DEFAULT.gritCount()).forGetter(ParadoxStateComponent::gritCount),
            Codec.STRING.optionalFieldOf("selected_lapse_mode", DEFAULT.selectedLapseMode().name())
                    .forGetter(state -> state.selectedLapseMode().name()),
            Codec.INT.optionalFieldOf("custom_lapse_seconds", DEFAULT.customLapseSeconds()).forGetter(ParadoxStateComponent::customLapseSeconds),
            UUID_CODEC.optionalFieldOf("item_uuid").forGetter(ParadoxStateComponent::itemUuid)
    ).apply(instance, (tier, gritCount, selectedLapseMode, customLapseSeconds, itemUuid) ->
            new ParadoxStateComponent(tier, gritCount, ParadoxLapseMode.valueOf(selectedLapseMode), customLapseSeconds, itemUuid)
    ));

    public static final PacketCodec<RegistryByteBuf, ParadoxStateComponent> PACKET_CODEC = new PacketCodec<>() {
        @Override
        public ParadoxStateComponent decode(RegistryByteBuf buf) {
            int tier = buf.readVarInt();
            int gritCount = buf.readVarInt();
            ParadoxLapseMode selectedLapseMode = ParadoxLapseMode.fromId(buf.readVarInt());
            int customLapseSeconds = buf.readVarInt();
            Optional<UUID> itemUuid = buf.readBoolean() ? Optional.of(buf.readUuid()) : Optional.empty();
            return new ParadoxStateComponent(tier, gritCount, selectedLapseMode, customLapseSeconds, itemUuid);
        }

        @Override
        public void encode(RegistryByteBuf buf, ParadoxStateComponent value) {
            buf.writeVarInt(value.tier());
            buf.writeVarInt(value.gritCount());
            buf.writeVarInt(value.selectedLapseMode().ordinal());
            buf.writeVarInt(value.customLapseSeconds());
            buf.writeBoolean(value.itemUuid().isPresent());
            value.itemUuid().ifPresent(buf::writeUuid);
        }
    };

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
