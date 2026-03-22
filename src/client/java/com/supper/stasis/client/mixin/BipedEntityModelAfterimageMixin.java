package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelAfterimageMixin {
	@Shadow
	public boolean sneaking;

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
	private void stasis$useAfterimageSneakingFlag(
			LivingEntity livingEntity,
			float limbAngle,
			float limbDistance,
			float animationProgress,
			float headYaw,
			float headPitch,
			CallbackInfo ci
	) {
		Boolean sneakingPose = AfterimageRenderState.getSneakingPoseOverride();
		if (sneakingPose != null) {
			this.sneaking = sneakingPose;
		}
	}
}
