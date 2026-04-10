package com.supper.stasis.network;

import com.supper.stasis.StasisPhase;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record StasisSyncPayload(
        StasisPhase phase,
        float progress,
        UUID activatingPlayerUUID,
        int activeTicksRemaining,
        int warningTicks
) implements FabricPacket {

    public static final Identifier ID_IDENTIFIER = Identifier.of("stasis", "sync");
    public static final PacketType<StasisSyncPayload> ID = PacketType.create(ID_IDENTIFIER, StasisSyncPayload::new);

    public StasisSyncPayload(PacketByteBuf buf) {
        this(
                StasisPhase.fromNetworkId(buf.readVarInt()),
                buf.readFloat(),
                buf.readBoolean() ? buf.readUuid() : null,
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeVarInt(this.phase().getNetworkId());
        buf.writeFloat(this.progress());
        buf.writeBoolean(this.activatingPlayerUUID() != null);
        if (this.activatingPlayerUUID() != null) {
            buf.writeUuid(this.activatingPlayerUUID());
        }
        buf.writeVarInt(this.activeTicksRemaining());
        buf.writeVarInt(this.warningTicks());
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
