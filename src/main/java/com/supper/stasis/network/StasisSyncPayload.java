package com.supper.stasis.network;

import com.supper.stasis.StasisPhase;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record StasisSyncPayload(
        StasisPhase phase,
        float progress,
        UUID activatingPlayerUUID,
        int activeTicksRemaining,
        int warningTicks
) implements CustomPayload {

    public static final Identifier ID_IDENTIFIER = Identifier.of("stasis", "sync");
    public static final CustomPayload.Id<StasisSyncPayload> ID = new CustomPayload.Id<>(ID_IDENTIFIER);

    public static final PacketCodec<RegistryByteBuf, StasisSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public StasisSyncPayload decode(RegistryByteBuf buf) {
            StasisPhase phase = StasisPhase.fromNetworkId(buf.readVarInt());
            float progress = buf.readFloat();
            UUID activatingPlayerUUID = buf.readBoolean() ? buf.readUuid() : null;
            int activeTicksRemaining = buf.readVarInt();
            int warningTicks = buf.readVarInt();
            return new StasisSyncPayload(phase, progress, activatingPlayerUUID, activeTicksRemaining, warningTicks);
        }

        @Override
        public void encode(RegistryByteBuf buf, StasisSyncPayload payload) {
            buf.writeVarInt(payload.phase().getNetworkId());
            buf.writeFloat(payload.progress());
            buf.writeBoolean(payload.activatingPlayerUUID() != null);
            if (payload.activatingPlayerUUID() != null) {
                buf.writeUuid(payload.activatingPlayerUUID());
            }
            buf.writeVarInt(payload.activeTicksRemaining());
            buf.writeVarInt(payload.warningTicks());
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
