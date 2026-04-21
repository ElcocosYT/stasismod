package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.texture.Sprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(OrderedRenderCommandQueueImpl.class)
public class OrderedRenderCommandQueueAfterimageMixin {
	@ModifyArgs(
			method = "submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/command/BatchingRenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
			)
	)
	private void stasis$applyAfterimageModelState(Args args) {
		stasis$applySpriteModelAfterimageState(args, 3, 6, 7);
	}

	@ModifyArgs(
			method = "submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;ZZILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/command/BatchingRenderCommandQueue;submitModelPart(Lnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IILnet/minecraft/client/texture/Sprite;ZZILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;I)V"
			)
	)
	private void stasis$applyAfterimageModelPartState(Args args) {
		stasis$applySpriteModelAfterimageState(args, 2, 8, 5);
	}

	private static void stasis$applySpriteModelAfterimageState(Args args, int renderLayerIndex, int tintedColorIndex, int spriteIndex) {
		if (!AfterimageRenderState.isActive()) {
			return;
		}

		Sprite sprite = args.get(spriteIndex);
		if (sprite == null) {
			return;
		}

		int tintedColor = args.get(tintedColorIndex);
		if (tintedColor != -1) {
			return;
		}

		RenderLayer translucentLayer = RenderLayers.itemEntityTranslucentCull(sprite.getAtlasId());
		args.set(renderLayerIndex, translucentLayer);
		args.set(tintedColorIndex, AfterimageRenderState.getColor());
	}
}
