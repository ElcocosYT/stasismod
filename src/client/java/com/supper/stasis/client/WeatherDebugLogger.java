package com.supper.stasis.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WeatherDebugLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("stasis/weather");
    private static final int LOG_INTERVAL = 40;
    private static int trailPassSamples = 0;
    private static int trailClearSamples = 0;
    private static int weatherHeadSamples = 0;
    private static int weatherTailSamples = 0;
    private static int splashSamples = 0;
    private static int lastFramebufferBinding = Integer.MIN_VALUE;

    private WeatherDebugLogger() {
    }

    public static int getFramebufferBinding() {
        return GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    public static void logTrailPass(
            String version,
            MinecraftClient client,
            String reason,
            boolean running,
            boolean snapshotsEmpty,
            boolean sodiumLoaded,
            boolean irisLoaded
    ) {
        int binding = getFramebufferBinding();
        if (!shouldLog(binding, trailPassSamples++)) {
            return;
        }

        LOGGER.debug(
                "[weather-debug/trail-pass/{}] reason={} running={} snapshotsEmpty={} sodium={} iris={} mainFb={} boundFb={} blend={} depthTest={} depthMask={} srcBlend={} dstBlend={}",
                version,
                reason,
                running,
                snapshotsEmpty,
                sodiumLoaded,
                irisLoaded,
                getFramebufferId(client != null ? client.getFramebuffer() : null),
                binding,
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != 0,
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        );
    }

    public static void logTrailFramebufferClear(
            String version,
            MinecraftClient client,
            Framebuffer trailFramebuffer,
            boolean shaderLoadedBeforeLookup,
            int beforeLookupBinding,
            int afterLookupBinding,
            int afterClearBinding
    ) {
        if (!shouldLog(afterClearBinding, trailClearSamples++)) {
            return;
        }

        LOGGER.debug(
                "[weather-debug/trail-clear/{}] shaderLoadedBeforeLookup={} mainFb={} trailFb={} beforeLookup={} afterLookup={} afterClear={} blend={} depthTest={} depthMask={} srcBlend={} dstBlend={}",
                version,
                shaderLoadedBeforeLookup,
                getFramebufferId(client != null ? client.getFramebuffer() : null),
                getFramebufferId(trailFramebuffer),
                beforeLookupBinding,
                afterLookupBinding,
                afterClearBinding,
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != 0,
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        );
    }

    public static void logWeatherRenderHead(
            String version,
            MinecraftClient client,
            int vanillaTicks,
            float vanillaTickDelta,
            int scaledTicks,
            float scaledTickDelta,
            boolean running,
            float rainGradient
    ) {
        int binding = getFramebufferBinding();
        if (!shouldLog(binding, weatherHeadSamples++)) {
            return;
        }

        LOGGER.debug(
                "[weather-debug/render-head/{}] running={} rainGradient={} vanillaTicks={} vanillaTickDelta={} scaledTicks={} scaledTickDelta={} mainFb={} boundFb={} blend={} depthTest={} depthMask={} srcBlend={} dstBlend={}",
                version,
                running,
                rainGradient,
                vanillaTicks,
                vanillaTickDelta,
                scaledTicks,
                scaledTickDelta,
                getFramebufferId(client != null ? client.getFramebuffer() : null),
                binding,
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != 0,
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        );
    }

    public static void logWeatherRenderTail(String version, MinecraftClient client, boolean running, float rainGradient) {
        int binding = getFramebufferBinding();
        if (!shouldLog(binding, weatherTailSamples++)) {
            return;
        }

        LOGGER.debug(
                "[weather-debug/render-tail/{}] running={} rainGradient={} mainFb={} boundFb={} blend={} depthTest={} depthMask={} srcBlend={} dstBlend={}",
                version,
                running,
                rainGradient,
                getFramebufferId(client != null ? client.getFramebuffer() : null),
                binding,
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != 0,
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        );
    }

    public static void logRainSplash(
            String version,
            MinecraftClient client,
            int vanillaTicks,
            int scaledTicks,
            boolean running,
            float rainGradient
    ) {
        int binding = getFramebufferBinding();
        if (!shouldLog(binding, splashSamples++)) {
            return;
        }

        LOGGER.debug(
                "[weather-debug/splash/{}] running={} rainGradient={} vanillaTicks={} scaledTicks={} mainFb={} boundFb={} blend={} depthTest={} depthMask={} srcBlend={} dstBlend={}",
                version,
                running,
                rainGradient,
                vanillaTicks,
                scaledTicks,
                getFramebufferId(client != null ? client.getFramebuffer() : null),
                binding,
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK) != 0,
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        );
    }

    private static boolean shouldLog(int framebufferBinding, int sampleIndex) {
        boolean bindingChanged = framebufferBinding != lastFramebufferBinding;
        lastFramebufferBinding = framebufferBinding;
        return sampleIndex < 12 || bindingChanged || sampleIndex % LOG_INTERVAL == 0;
    }

    private static int getFramebufferId(Framebuffer framebuffer) {
        return framebuffer != null ? framebuffer.fbo : -1;
    }
}
