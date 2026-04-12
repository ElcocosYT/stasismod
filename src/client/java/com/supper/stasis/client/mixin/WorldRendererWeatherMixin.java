package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import com.supper.stasis.client.WeatherDebugLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererWeatherMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	private int ticks;

	@Unique
	private int stasis$weatherTicks;

	@Unique
	private float stasis$weatherTickProgress;

	@ModifyVariable(
			method = "renderWeather",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private float stasis$scaleWeatherTickDelta(float tickDelta) {
		this.stasis$weatherTicks = StasisClientState.getPrecipitationTicks(this.ticks, tickDelta);
		this.stasis$weatherTickProgress = StasisClientState.getPrecipitationSubTickProgress(this.ticks, tickDelta);
		WeatherDebugLogger.logWeatherRenderHead(
				"1.21.1",
				this.client,
				this.ticks,
				tickDelta,
				this.stasis$weatherTicks,
				this.stasis$weatherTickProgress,
				StasisClientState.isRunning(),
				this.client.world != null ? this.client.world.getRainGradient(tickDelta) : 0.0f
		);
		return this.stasis$weatherTickProgress;
	}

	@Inject(method = "renderWeather", at = @At("RETURN"))
	private void stasis$logWeatherRenderTail(
			LightmapTextureManager lightmapTextureManager,
			float tickDelta,
			double cameraX,
			double cameraY,
			double cameraZ,
			CallbackInfo ci
	) {
		WeatherDebugLogger.logWeatherRenderTail(
				"1.21.1",
				this.client,
				StasisClientState.isRunning(),
				this.client.world != null ? this.client.world.getRainGradient(tickDelta) : 0.0f
		);
	}

	@Redirect(
			method = "renderWeather",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/client/render/WorldRenderer;ticks:I",
					opcode = Opcodes.GETFIELD
			)
	)
	private int stasis$scaleWeatherTicks(WorldRenderer instance) {
		return this.stasis$weatherTicks;
	}

	@Redirect(
			method = "tickRainSplashing",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/client/render/WorldRenderer;ticks:I",
					opcode = Opcodes.GETFIELD
			)
	)
	private int stasis$scaleRainSplashTicks(WorldRenderer instance) {
		return StasisClientState.getPrecipitationTicks(this.ticks);
	}

	@Inject(method = "tickRainSplashing", at = @At("HEAD"))
	private void stasis$logRainSplashing(Camera camera, CallbackInfo ci) {
		WeatherDebugLogger.logRainSplash(
				"1.21.1",
				this.client,
				this.ticks,
				StasisClientState.getPrecipitationTicks(this.ticks),
				StasisClientState.isRunning(),
				this.client.world != null ? this.client.world.getRainGradient(1.0f) : 0.0f
		);
	}
}
