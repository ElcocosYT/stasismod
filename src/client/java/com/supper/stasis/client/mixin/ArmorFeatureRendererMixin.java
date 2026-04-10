package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import com.supper.stasis.client.render.AfterimageTintingVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public class ArmorFeatureRendererMixin {
	@Redirect(
			method = "renderArmorParts",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
			)
	)
	private RenderLayer stasis$useAfterimageArmorLayer(Identifier texture) {
		return AfterimageRenderState.isActive()
				? RenderLayer.getEntityTranslucent(texture)
				: RenderLayer.getArmorCutoutNoCull(texture);
	}

	@ModifyVariable(
			method = "renderArmorParts",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private VertexConsumerProvider stasis$wrapAfterimageArmorVertexConsumers(VertexConsumerProvider vertexConsumers) {
		return AfterimageTintingVertexConsumerProvider.wrap(vertexConsumers);
	}

	@Inject(method = "renderTrim", at = @At("HEAD"), cancellable = true)
	private void stasis$skipArmorTrim(CallbackInfo ci) {
		if (AfterimageRenderState.isActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderGlint", at = @At("HEAD"), cancellable = true)
	private void stasis$skipArmorGlint(CallbackInfo ci) {
		if (AfterimageRenderState.isActive()) {
			ci.cancel();
		}
	}
}
