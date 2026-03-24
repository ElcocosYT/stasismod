package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("RETURN"))
    private void stasis$stabilizeRestrictedLocalPlayerAfterCorrection(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || !StasisClientState.isRestrictedLocalPlayer(player)) {
            return;
        }

        StasisClientState.stabilizeRestrictedLocalPlayer(player);
    }
}
