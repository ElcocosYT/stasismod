package com.supper.stasis.network;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record ParadoxSetCustomLapsePayload(int syncId, int totalSeconds) implements FabricPacket {
    public static final Identifier ID_IDENTIFIER = Identifier.of("stasis", "paradox_set_custom_lapse");
    public static final PacketType<ParadoxSetCustomLapsePayload> ID = PacketType.create(ID_IDENTIFIER, ParadoxSetCustomLapsePayload::new);

    public ParadoxSetCustomLapsePayload(PacketByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(this.syncId());
        buf.writeVarInt(this.totalSeconds());
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
