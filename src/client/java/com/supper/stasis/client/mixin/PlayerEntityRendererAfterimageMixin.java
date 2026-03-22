package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererAfterimageMixin {
	@Inject(
			method = "getPositionOffset(Lnet/minecraft/client/network/AbstractClientPlayerEntity;F)Lnet/minecraft/util/math/Vec3d;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void stasis$useAfterimagePositionOffset(
			AbstractClientPlayerEntity player,
			float tickDelta,
			CallbackInfoReturnable<Vec3d> cir
	) {
		Boolean sneakingPose = AfterimageRenderState.getSneakingPoseOverride();
		if (sneakingPose == null) {
			return;
		}

		if (sneakingPose) {
			cir.setReturnValue(new Vec3d(0.0, player.getScale() * -2.0F / 16.0, 0.0));
			return;
		}

		cir.setReturnValue(Vec3d.ZERO);
	}
}
