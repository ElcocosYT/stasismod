package com.supper.stasis.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ParadoxSetCustomLapsePayload(int syncId, int totalSeconds) implements CustomPayload {
    public static final Identifier ID_IDENTIFIER = Identifier.of("stasis", "paradox_set_custom_lapse");
    public static final CustomPayload.Id<ParadoxSetCustomLapsePayload> ID = new CustomPayload.Id<>(ID_IDENTIFIER);

    public static final PacketCodec<RegistryByteBuf, ParadoxSetCustomLapsePayload> CODEC = new PacketCodec<>() {
        @Override
        public ParadoxSetCustomLapsePayload decode(RegistryByteBuf buf) {
            return new ParadoxSetCustomLapsePayload(buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void encode(RegistryByteBuf buf, ParadoxSetCustomLapsePayload value) {
            buf.writeVarInt(value.syncId());
            buf.writeVarInt(value.totalSeconds());
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
