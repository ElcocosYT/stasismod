package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityRenderer.class)
public class BipedEntityRendererAfterimageMixin {
	@Inject(
			method = "updateBipedRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;FLnet/minecraft/client/item/ItemModelManager;)V",
			at = @At("RETURN")
	)
	private static void stasis$applyAfterimageSneakingPose(
			LivingEntity entity,
			BipedEntityRenderState renderState,
			float tickProgress,
			ItemModelManager itemModelManager,
			CallbackInfo ci
	) {
		Boolean sneakingPose = AfterimageRenderState.getSneakingPoseOverride();
		if (sneakingPose != null) {
			renderState.isInSneakingPose = sneakingPose;
		}
	}
}
