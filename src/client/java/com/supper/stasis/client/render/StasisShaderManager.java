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
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebufferFactory;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Pool;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StasisShaderManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("stasis");
	private static final Identifier SHADER_ID = Identifier.of("stasis", "stasis_grayscale");
	private static final Identifier TRAIL_TARGET_ID = Identifier.ofVanilla("trail_color");
	private static final String STASIS_CONFIG_UNIFORM = "StasisConfig";

	private static PostEffectProcessor shaderEffect = null;
	private static int shaderWidth = -1;
	private static int shaderHeight = -1;
	private static Framebuffer cachedTrailFramebuffer = null;
	private static int cachedTrailFbWidth = -1;
	private static int cachedTrailFbHeight = -1;

	private StasisShaderManager() {
	}

	public static void ensureLoaded(GameRenderer gameRenderer) {
		int width = gameRenderer.getClient().getFramebuffer().textureWidth;
		int height = gameRenderer.getClient().getFramebuffer().textureHeight;
		if (shaderEffect == null || width != shaderWidth || height != shaderHeight) {
			loadShader(gameRenderer, width, height);
		}
	}

	private static void loadShader(GameRenderer gameRenderer, int width, int height) {
		cleanup();
		try {
			PostEffectProcessor loadedShader = gameRenderer.getClient().getShaderLoader().loadPostEffect(SHADER_ID, Set.of(PostEffectProcessor.MAIN));
			if (loadedShader == null) {
				LOGGER.error("Failed to load stasis shader {}", SHADER_ID);
				shaderWidth = -1;
				shaderHeight = -1;
				return;
			}

			shaderEffect = loadedShader;
			shaderWidth = width;
			shaderHeight = height;
		} catch (Exception exception) {
			LOGGER.error("Failed to load stasis shader", exception);
			shaderEffect = null;
			shaderWidth = -1;
			shaderHeight = -1;
		}
	}

	public static void render(GameRenderer gameRenderer, float progress) {
		try {
			ensureLoaded(gameRenderer);
			if (shaderEffect == null) {
				return;
			}

			float clampedProgress = Math.max(0.0f, Math.min(1.0f, progress));
			float radius = clampedProgress * 1.15f;
			stasis$updateStasisConfig(clampedProgress, radius);
			Pool pool = ((GameRendererPoolAccessor) gameRenderer).stasis$getPool();
			shaderEffect.render(gameRenderer.getClient().getFramebuffer(), pool);
		} catch (Exception exception) {
			LOGGER.error("Error rendering stasis shader", exception);
			cleanup();
		}
	}

	public static void cleanup() {
		cachedTrailFramebuffer = null;
		cachedTrailFbWidth = -1;
		cachedTrailFbHeight = -1;
		// ShaderLoader caches PostEffectProcessor instances internally.
		// If we close the processor here, the next activation may receive the
		// same cached instance with its internal buffers already closed.
		shaderEffect = null;
		shaderWidth = -1;
		shaderHeight = -1;
	}

	public static Framebuffer getTrailFramebuffer(GameRenderer gameRenderer) {
		ensureLoaded(gameRenderer);
		if (shaderEffect == null) {
			return null;
		}

		Framebuffer mainFramebuffer = gameRenderer.getClient().getFramebuffer();
		int width = mainFramebuffer.textureWidth;
		int height = mainFramebuffer.textureHeight;
		
		// Reuse cached framebuffer if dimensions match
		if (cachedTrailFramebuffer != null && cachedTrailFbWidth == width && cachedTrailFbHeight == height) {
			return cachedTrailFramebuffer;
		}
		
		// Let PostEffectProcessor manage the lifecycle of its own named targets.
		// Its createFramebuffer path already reuses or recreates the framebuffer
		// safely when dimensions change.
		cachedTrailFramebuffer = ((PostEffectProcessorAccessor) shaderEffect).stasis$createFramebuffer(
				TRAIL_TARGET_ID,
				new SimpleFramebufferFactory(width, height, true, 0)
		);
		cachedTrailFbWidth = width;
		cachedTrailFbHeight = height;
		
		return cachedTrailFramebuffer;
	}

	public static void clearTrailFramebuffer(GameRenderer gameRenderer) {
		Framebuffer trailFramebuffer = getTrailFramebuffer(gameRenderer);
		if (trailFramebuffer == null) {
			return;
		}

		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(trailFramebuffer.getColorAttachment(), 0);
		if (trailFramebuffer.useDepthAttachment && trailFramebuffer.getDepthAttachment() != null) {
			RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(trailFramebuffer.getDepthAttachment(), 1.0);
		}
	}

	private static void stasis$updateStasisConfig(float progress, float radius) {
		if (shaderEffect == null) {
			return;
		}

		List<PostEffectPass> passes = ((PostEffectProcessorAccessor) shaderEffect).stasis$getPasses();
		if (passes == null) {
			return;
		}

		for (PostEffectPass pass : passes) {
			Map<String, GpuBuffer> uniformBuffers = ((PostEffectPassAccessor) pass).stasis$getUniformBuffers();
			GpuBuffer uniformBuffer = uniformBuffers != null ? uniformBuffers.get(STASIS_CONFIG_UNIFORM) : null;
			if (uniformBuffer == null) {
				continue;
			}

			uniformBuffer = stasis$getWritableUniformBuffer(pass, uniformBuffers, uniformBuffer);
			try (GpuBuffer.MappedView mappedView = RenderSystem.getDevice().createCommandEncoder().mapBuffer(uniformBuffer, false, true)) {
				mappedView.data()
						.order(ByteOrder.nativeOrder())
						.putFloat(0, progress)
						.putFloat(4, radius);
			}
		}
	}

	private static GpuBuffer stasis$getWritableUniformBuffer(PostEffectPass pass, Map<String, GpuBuffer> uniformBuffers, GpuBuffer uniformBuffer) {
		if (!uniformBuffer.isClosed() && (uniformBuffer.usage() & GpuBuffer.USAGE_MAP_WRITE) != 0) {
			return uniformBuffer;
		}

		int passId = System.identityHashCode(pass);
		GpuBuffer writableBuffer = RenderSystem.getDevice().createBuffer(
				() -> "stasis/" + STASIS_CONFIG_UNIFORM + "/" + Integer.toHexString(passId),
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_HINT_CLIENT_STORAGE,
				uniformBuffer.size()
		);
		uniformBuffers.put(STASIS_CONFIG_UNIFORM, writableBuffer);
		return writableBuffer;
	}
}
