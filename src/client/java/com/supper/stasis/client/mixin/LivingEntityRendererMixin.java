package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> {
	private static final Identifier STASIS_AFTERIMAGE_TEXTURE = Identifier.of("minecraft", "textures/misc/white.png");

	@Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
	private void stasis$useTranslucentLayer(T entity, boolean showBody, boolean translucent, boolean showOutline, CallbackInfoReturnable<RenderLayer> cir) {
		if (!AfterimageRenderState.isActive()) {
			return;
		}

		cir.setReturnValue(RenderLayer.getEntityTranslucentEmissive(STASIS_AFTERIMAGE_TEXTURE));
	}

	@ModifyVariable(
			method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 1
	)
	private float stasis$useEntityTransitionTickDelta(
			float tickDelta,
			T entity,
			float yaw,
			float originalTickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		return StasisClientState.getEntityRenderTickDelta(entity, tickDelta);
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"
			),
			index = 4
	)
	private float stasis$applyAfterimageRed(float red) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getRedFloat() : red;
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"
			),
			index = 5
	)
	private float stasis$applyAfterimageGreen(float green) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getGreenFloat() : green;
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"
			),
			index = 6
	)
	private float stasis$applyAfterimageBlue(float blue) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getBlueFloat() : blue;
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"
			),
			index = 7
	)
	private float stasis$applyAfterimageAlpha(float alpha) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getAlphaFloat() : alpha;
	}
}
