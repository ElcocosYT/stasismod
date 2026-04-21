package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.tr7zw.notenoughanimations.logic.PlayerTransformer", remap = false)
public abstract class NotEnoughAnimationsPlayerTransformerMixin {
	// Not Enough Animations reapplies live player motion on top of the frozen trail snapshot.
	// Skip its transformer entirely while an afterimage is rendering so the ghost keeps its saved pose.
	@Inject(method = { "preUpdate", "updateModel" }, at = @At("HEAD"), cancellable = true, remap = false)
	private void stasis$skipTrailAnimationTransform(
			AbstractClientPlayerEntity player,
			PlayerEntityModel<?> model,
			float tickDelta,
			CallbackInfo originalCallback,
			CallbackInfo ci
	) {
		if (AfterimageRenderState.isActive()) {
			ci.cancel();
		}
	}
}
