package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
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
				? new AfterimageCapeVertexConsumerProvider(vertexConsumers)
				: vertexConsumers;
	}

	private static final class AfterimageCapeVertexConsumerProvider implements VertexConsumerProvider {
		private final VertexConsumerProvider delegate;

		private AfterimageCapeVertexConsumerProvider(VertexConsumerProvider delegate) {
			this.delegate = delegate;
		}

		@Override
		public VertexConsumer getBuffer(RenderLayer layer) {
			return new AfterimageCapeVertexConsumer(this.delegate.getBuffer(layer));
		}
	}

	private static final class AfterimageCapeVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;

		private AfterimageCapeVertexConsumer(VertexConsumer delegate) {
			this.delegate = delegate;
		}

		@Override
		public VertexConsumer vertex(float x, float y, float z) {
			this.delegate.vertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer color(int red, int green, int blue, int alpha) {
			this.delegate.color(
					multiply(red, AfterimageRenderState.getRedFloat()),
					multiply(green, AfterimageRenderState.getGreenFloat()),
					multiply(blue, AfterimageRenderState.getBlueFloat()),
					multiply(alpha, AfterimageRenderState.getAlphaFloat())
			);
			return this;
		}

		@Override
		public VertexConsumer texture(float u, float v) {
			this.delegate.texture(u, v);
			return this;
		}

		@Override
		public VertexConsumer overlay(int u, int v) {
			this.delegate.overlay(u, v);
			return this;
		}

		@Override
		public VertexConsumer light(int u, int v) {
			this.delegate.light(u, v);
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			this.delegate.normal(x, y, z);
			return this;
		}

		private static int multiply(int channel, float multiplier) {
			return Math.max(0, Math.min(255, Math.round(channel * multiplier)));
		}
	}
}
