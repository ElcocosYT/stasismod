package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import com.supper.stasis.client.render.AfterimageTintingVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CapeFeatureRenderer.class)
public class CapeFeatureRendererMixin {
	@Redirect(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/RenderLayer;getEntitySolid(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
			)
	)
	private RenderLayer stasis$useAfterimageCapeLayer(Identifier texture) {
		return AfterimageRenderState.isActive()
				? RenderLayer.getEntityTranslucent(texture)
				: RenderLayer.getEntitySolid(texture);
	}

	@ModifyVariable(
			method = "render",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private VertexConsumerProvider stasis$wrapAfterimageCapeVertexConsumers(VertexConsumerProvider vertexConsumers) {
		return AfterimageRenderState.isActive()
				? AfterimageTintingVertexConsumerProvider.wrap(vertexConsumers)
				: vertexConsumers;
	}
}
