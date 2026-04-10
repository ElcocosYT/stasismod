package com.supper.stasis.client.render;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StasisShaderManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("stasis");
	private static final Identifier SHADER_ID = Identifier.of("stasis", "shaders/post/stasis_grayscale.json");

	private static PostEffectProcessor shaderEffect = null;
	private static int shaderWidth = -1;
	private static int shaderHeight = -1;

	private StasisShaderManager() {
	}

	public static void ensureLoaded(GameRenderer gameRenderer) {
		if (shaderEffect == null) {
			loadShader(gameRenderer);
			return;
		}

		int width = gameRenderer.getClient().getFramebuffer().textureWidth;
		int height = gameRenderer.getClient().getFramebuffer().textureHeight;
		if (width != shaderWidth || height != shaderHeight) {
			shaderEffect.setupDimensions(width, height);
			shaderWidth = width;
			shaderHeight = height;
		}
	}

	public static void loadShader(GameRenderer gameRenderer) {
		cleanup();
		try {
			int width = gameRenderer.getClient().getFramebuffer().textureWidth;
			int height = gameRenderer.getClient().getFramebuffer().textureHeight;
			shaderEffect = new PostEffectProcessor(
					gameRenderer.getClient().getTextureManager(),
					gameRenderer.getClient().getResourceManager(),
					gameRenderer.getClient().getFramebuffer(),
					SHADER_ID
			);
			shaderEffect.setupDimensions(width, height);
			shaderWidth = width;
			shaderHeight = height;
			LOGGER.info("Stasis shader loaded successfully");
		} catch (Exception exception) {
			LOGGER.error("Failed to load stasis shader", exception);
			shaderEffect = null;
			shaderWidth = -1;
			shaderHeight = -1;
		}
	}

	public static void render(GameRenderer gameRenderer, float tickDelta, float progress) {
		try {
			ensureLoaded(gameRenderer);
			if (shaderEffect == null) {
				return;
			}

			float clampedProgress = Math.max(0.0f, Math.min(1.0f, progress));
			float radius = clampedProgress * 1.15f;
			applyUniform("Progress", clampedProgress);
			applyUniform("Radius", radius);
			shaderEffect.render(tickDelta);
		} catch (Exception exception) {
			LOGGER.error("Error rendering stasis shader", exception);
			cleanup();
		}
	}

	public static void cleanup() {
		if (shaderEffect != null) {
			shaderEffect.close();
			shaderEffect = null;
		}
		shaderWidth = -1;
		shaderHeight = -1;
	}

	public static PostEffectProcessor getShader() {
		return shaderEffect;
	}

	public static Framebuffer getTrailFramebuffer(GameRenderer gameRenderer) {
		ensureLoaded(gameRenderer);
		return shaderEffect != null ? shaderEffect.getSecondaryTarget("trail_color") : null;
	}

	private static void applyUniform(String uniformName, float value) {
		if (shaderEffect == null) {
			return;
		}

		for (PostEffectPass pass : ((com.supper.stasis.client.mixin.PostEffectProcessorAccessor) shaderEffect).stasis$getPasses()) {
			pass.getProgram().getUniformByNameOrDummy(uniformName).set(value);
			pass.getProgram().markUniformsDirty();
		}
	}
}
