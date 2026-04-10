package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ItemRenderer.class)
public class ItemRendererAfterimageMixin {
	@ModifyArg(
			method = "renderBakedItemQuads",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFII)V"
			),
			index = 2
	)
	private static float stasis$applyItemRed(float red) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getRedFloat() : red;
	}

	@ModifyArg(
			method = "renderBakedItemQuads",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFII)V"
			),
			index = 3
	)
	private static float stasis$applyItemGreen(float green) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getGreenFloat() : green;
	}

	@ModifyArg(
			method = "renderBakedItemQuads",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFII)V"
			),
			index = 4
	)
	private static float stasis$applyItemBlue(float blue) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getBlueFloat() : blue;
	}

	@ModifyArg(
			method = "renderBakedItemQuads",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFII)V"
			),
			index = 5
	)
	private static float stasis$applyItemAlpha(float alpha) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getAlphaFloat() : alpha;
	}
}
