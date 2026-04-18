package com.supper.stasis.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.supper.stasis.client.mixin.GameRendererPoolAccessor;
import com.supper.stasis.client.mixin.PostEffectPassAccessor;
import com.supper.stasis.client.mixin.PostEffectProcessorAccessor;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Pool;
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
	private static final Identifier SHADER_ID = Identifier.of("stasis", "stasis_shockwave");
	private static final String SHOCKWAVE_CONFIG_UNIFORM = "ShockwaveConfig";

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
	public static void render(GameRenderer gameRenderer) {
		if (!active) {
			return;
		}

		float tickDelta = 0.0f;
		try {
			tickDelta = gameRenderer.getClient().getRenderTickCounter().getTickProgress(false);
		} catch (Exception ignored) {
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

			updateShockwaveConfig(progress);
			Pool pool = ((GameRendererPoolAccessor) gameRenderer).stasis$getPool();
			shaderEffect.render(gameRenderer.getClient().getFramebuffer(), pool);
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
			PostEffectProcessor loaded = gameRenderer.getClient().getShaderLoader().loadPostEffect(
					SHADER_ID, Set.of(PostEffectProcessor.MAIN)
			);
			if (loaded == null) {
				LOGGER.error("Failed to load shockwave shader {}", SHADER_ID);
				return;
			}
			shaderEffect = loaded;
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
		shaderEffect = null;
		shaderWidth = -1;
		shaderHeight = -1;
	}


	private static void updateShockwaveConfig(float progress) {
		if (shaderEffect == null) {
			return;
		}

		List<PostEffectPass> passes = ((PostEffectProcessorAccessor) shaderEffect).stasis$getPasses();
		if (passes == null) {
			return;
		}

		for (PostEffectPass pass : passes) {
			Map<String, GpuBuffer> uniformBuffers = ((PostEffectPassAccessor) pass).stasis$getUniformBuffers();
			GpuBuffer uniformBuffer = uniformBuffers != null ? uniformBuffers.get(SHOCKWAVE_CONFIG_UNIFORM) : null;
			if (uniformBuffer == null) {
				continue;
			}

			uniformBuffer = getWritableUniformBuffer(pass, uniformBuffers, uniformBuffer);
			try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(uniformBuffer, false, true)) {
				mappedView.data()
						.order(ByteOrder.nativeOrder())
						.putFloat(0, progress);
			}
		}
	}


	private static GpuBuffer getWritableUniformBuffer(PostEffectPass pass, Map<String, GpuBuffer> uniformBuffers, GpuBuffer uniformBuffer) {
		if (!uniformBuffer.isClosed() && (uniformBuffer.usage() & GpuBuffer.USAGE_MAP_WRITE) != 0) {
			return uniformBuffer;
		}

		int passId = System.identityHashCode(pass);
		GpuBuffer writableBuffer = RenderSystem.getDevice().createBuffer(
				() -> "stasis/" + SHOCKWAVE_CONFIG_UNIFORM + "/" + Integer.toHexString(passId),
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_HINT_CLIENT_STORAGE,
				uniformBuffer.size()
		);
		uniformBuffers.put(SHOCKWAVE_CONFIG_UNIFORM, writableBuffer);
		return writableBuffer;
	}
}
