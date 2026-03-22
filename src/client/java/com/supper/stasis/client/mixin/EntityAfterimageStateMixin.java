package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityAfterimageStateMixin {
	@Inject(method = "getPose", at = @At("HEAD"), cancellable = true)
	private void stasis$useAfterimagePose(CallbackInfoReturnable<EntityPose> cir) {
		EntityPose pose = AfterimageRenderState.getPoseOverride();
		if (pose != null) {
			cir.setReturnValue(pose);
		}
	}

	@Inject(method = "isSneaking", at = @At("HEAD"), cancellable = true)
	private void stasis$useAfterimageSneaking(CallbackInfoReturnable<Boolean> cir) {
		Boolean sneaking = AfterimageRenderState.getSneakingOverride();
		if (sneaking != null) {
			cir.setReturnValue(sneaking);
		}
	}

	@Inject(method = "isInSneakingPose", at = @At("HEAD"), cancellable = true)
	private void stasis$useAfterimageSneakingPose(CallbackInfoReturnable<Boolean> cir) {
		Boolean sneakingPose = AfterimageRenderState.getSneakingPoseOverride();
		if (sneakingPose != null) {
			cir.setReturnValue(sneakingPose);
		}
	}
}
