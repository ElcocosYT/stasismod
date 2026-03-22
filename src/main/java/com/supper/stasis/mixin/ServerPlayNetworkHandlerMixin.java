package com.supper.stasis.mixin;

import com.supper.stasis.StasisManager;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
	@Shadow public ServerPlayerEntity player;

	@Shadow public abstract void syncWithPlayerPosition();

	private boolean stasis$shouldFreezePlayer() {
		return StasisManager.getInstance().isRestrictedPlayer(player);
	}

	@Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeMovement(PlayerMoveC2SPacket packet, CallbackInfo ci) {
		if (stasis$shouldFreezePlayer()) {
			syncWithPlayerPosition();
			ci.cancel();
		}
	}

	@Inject(method = "onVehicleMove", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeVehicleMovement(VehicleMoveC2SPacket packet, CallbackInfo ci) {
		if (stasis$shouldFreezePlayer()) {
			syncWithPlayerPosition();
			ci.cancel();
		}
	}

	@Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeInputs(PlayerInputC2SPacket packet, CallbackInfo ci) {
		if (stasis$shouldFreezePlayer()) {
			ci.cancel();
		}
	}

	@Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeActions(PlayerActionC2SPacket packet, CallbackInfo ci) {
		if (stasis$shouldFreezePlayer()) {
			ci.cancel();
		}
	}

	@Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeBlockInteraction(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
		NetworkThreadUtils.forceMainThread(packet, (ServerPlayNetworkHandler)(Object)this, this.player.getServerWorld());
		StasisManager manager = StasisManager.getInstance();
		if (manager.shouldFreezeWorld(player.getWorld()) && !manager.isRestrictedPlayer(player)) {
			if (manager.queueRedstoneInteraction(player, packet.getHand(), packet.getBlockHitResult())) {
				ci.cancel();
				return;
			}
			manager.debugFrozenBlockInteractionPassthrough(player, packet.getBlockHitResult());
		}

		if (stasis$shouldFreezePlayer()) {
			ci.cancel();
		}
	}

	@Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeItemInteraction(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
		if (stasis$shouldFreezePlayer()) {
			ci.cancel();
		}
	}

	@Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeEntityInteraction(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
		if (stasis$shouldFreezePlayer()) {
			ci.cancel();
		}
	}

	@Inject(method = "onHandSwing", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeHandSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
		if (stasis$shouldFreezePlayer()) {
			ci.cancel();
		}
	}
}
