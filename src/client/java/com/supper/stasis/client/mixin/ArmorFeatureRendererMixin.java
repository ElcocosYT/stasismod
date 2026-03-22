package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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

	@ModifyArg(
			method = "renderArmorParts",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
			),
			index = 4
	)
	private int stasis$applyAfterimageArmorColor(int color) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getColor() : color;
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
