package com.supper.stasis.client.render;

import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a Gojo-style shockwave distortion effect when stasis activates.
 *
 * The shockwave is a screen-space post-processing shader that creates
 * an expanding ring of UV distortion with purple/blue tint and
 * chromatic aberration, triggered at the start of TRANSITION_IN.
 */
public final class ShockwaveRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger("stasis");
	private static final Identifier SHADER_ID = Identifier.of("stasis", "shaders/post/stasis_shockwave.json");

	/** Duration in ticks (~1.5 seconds at 20 TPS). */
	private static final int DURATION_TICKS = 30;

	private static PostEffectProcessor shaderEffect = null;
	private static int shaderWidth = -1;
	private static int shaderHeight = -1;

	private static boolean active = false;
	private static int elapsedTicks = 0;

	private ShockwaveRenderer() {
	}


	/** Called when TRANSITION_IN begins to start the shockwave animation. */
	public static void trigger() {
		active = true;
		elapsedTicks = 0;
	}


	/** Advances the shockwave one tick. Called from StasisClientState.apply(). */
	public static void tick() {
		if (!active) {
			return;
		}
		elapsedTicks++;
		if (elapsedTicks >= DURATION_TICKS) {
			active = false;
			cleanupShader();
		}
	}


	public static boolean isActive() {
		return active;
	}


	/**
	 * Returns the shockwave progress 0.0→1.0 with sub-tick interpolation.
	 */
	public static float getProgress(float tickDelta) {
		if (!active) {
			return 1.0f;
		}
		float t = (elapsedTicks + tickDelta) / (float) DURATION_TICKS;
		return Math.min(t, 1.0f);
	}


	/** Renders the shockwave shader pass. Called from GameRendererMixin. */
	public static void render(GameRenderer gameRenderer, float tickDelta) {
		if (!active) {
			return;
		}

		float progress = getProgress(tickDelta);
		if (progress >= 1.0f) {
			active = false;
			cleanupShader();
			return;
		}

		try {
			ensureLoaded(gameRenderer);
			if (shaderEffect == null) {
				return;
			}

			shaderEffect.setUniforms("ShockwaveProgress", progress);
			shaderEffect.render(tickDelta);
		} catch (Exception e) {
			LOGGER.error("Error rendering shockwave shader", e);
			cleanupShader();
			active = false;
		}
	}


	private static void ensureLoaded(GameRenderer gameRenderer) {
		int width = gameRenderer.getClient().getFramebuffer().textureWidth;
		int height = gameRenderer.getClient().getFramebuffer().textureHeight;

		if (shaderEffect == null || width != shaderWidth || height != shaderHeight) {
			loadShader(gameRenderer, width, height);
		}
	}


	private static void loadShader(GameRenderer gameRenderer, int width, int height) {
		cleanupShader();
		try {
			shaderEffect = new PostEffectProcessor(
					gameRenderer.getClient().getTextureManager(),
					gameRenderer.getClient().getResourceManager(),
					gameRenderer.getClient().getFramebuffer(),
					SHADER_ID
			);
			shaderEffect.setupDimensions(width, height);
			shaderWidth = width;
			shaderHeight = height;
		} catch (Exception e) {
			LOGGER.error("Failed to load shockwave shader", e);
			shaderEffect = null;
			shaderWidth = -1;
			shaderHeight = -1;
		}
	}


	private static void cleanupShader() {
		if (shaderEffect != null) {
			shaderEffect.close();
			shaderEffect = null;
		}
		shaderWidth = -1;
		shaderHeight = -1;
	}
}
