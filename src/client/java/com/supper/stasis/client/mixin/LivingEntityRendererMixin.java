package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	// 1.21.11 no longer ships the old vanilla misc/white texture, so keep a local
	// white mask to preserve the same cyan afterimage tint that 1.21.1 used.
	private static final Identifier STASIS_AFTERIMAGE_TEXTURE = Identifier.of("stasis", "textures/misc/white.png");

	@Inject(
			method = "getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void stasis$useTranslucentLayer(
			LivingEntityRenderState renderState,
			boolean showBody,
			boolean translucent,
			boolean showOutline,
			CallbackInfoReturnable<RenderLayer> cir
	) {
		if (AfterimageRenderState.isActive()) {
			cir.setReturnValue(RenderLayers.entityTranslucentEmissive(STASIS_AFTERIMAGE_TEXTURE));
		}
	}

	@Inject(
			method = "getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I",
			at = @At("HEAD"),
			cancellable = true
	)
	private void stasis$applyAfterimageColor(LivingEntityRenderState renderState, CallbackInfoReturnable<Integer> cir) {
		if (AfterimageRenderState.isActive()) {
			cir.setReturnValue(AfterimageRenderState.getColor());
		}
	}
}
