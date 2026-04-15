package com.supper.stasis.client.mixin;

import com.supper.stasis.Stasis;
import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeFeatureRenderer.class)
public class CapeFeatureRendererMixin {
	@Inject(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void stasis$skipCapeIfDisabled(MatrixStack matrices, OrderedRenderCommandQueue commandQueue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
		if (AfterimageRenderState.isActive() && !Stasis.CONFIG.trailsRenderCapes()) {
			ci.cancel();
		}
	}

	@Redirect(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/RenderLayers;entitySolid(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
			)
	)
	private RenderLayer stasis$useAfterimageCapeLayer(Identifier texture) {
		return AfterimageRenderState.isActive()
				? RenderLayers.entityTranslucent(texture)
				: RenderLayers.entitySolid(texture);
	}

	@Redirect(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
			)
	)
	@SuppressWarnings({"rawtypes", "unchecked"})
	private void stasis$submitAfterimageCapeModel(
			OrderedRenderCommandQueue commandQueue,
			Model model,
			Object state,
			MatrixStack matrices,
			RenderLayer renderLayer,
			int light,
			int overlay,
			int outlineColor,
			ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay
	) {
		int tintedColor = AfterimageRenderState.isActive() ? AfterimageRenderState.getColor() : -1;
		commandQueue.submitModel(model, state, matrices, renderLayer, light, overlay, tintedColor, null, outlineColor, crumblingOverlay);
	}
}
