package com.supper.stasis.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

public final class AfterimageTintingVertexConsumerProvider implements VertexConsumerProvider {
	private final VertexConsumerProvider delegate;

	private AfterimageTintingVertexConsumerProvider(VertexConsumerProvider delegate) {
		this.delegate = delegate;
	}

	public static VertexConsumerProvider wrap(VertexConsumerProvider provider) {
		if (!AfterimageRenderState.isActive() || provider instanceof AfterimageTintingVertexConsumerProvider) {
			return provider;
		}

		return new AfterimageTintingVertexConsumerProvider(provider);
	}

	@Override
	public VertexConsumer getBuffer(RenderLayer layer) {
		return new AfterimageTintingVertexConsumer(this.delegate.getBuffer(layer));
	}

	private static final class AfterimageTintingVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;

		private AfterimageTintingVertexConsumer(VertexConsumer delegate) {
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
