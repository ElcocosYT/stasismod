package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.render.WeatherRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {
	@ModifyVariable(
			method = "buildPrecipitationPieces",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private int stasis$scaleBuildPrecipitationTicks(int ticks) {
		return StasisClientState.getCapturedPrecipitationBuildTicks(ticks);
	}

	@ModifyVariable(
			method = "buildPrecipitationPieces",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private float stasis$scalePrecipitationTickProgress(float tickProgress) {
		return StasisClientState.getCapturedPrecipitationTickProgress(tickProgress);
	}

	@ModifyVariable(
			method = "addParticlesAndSound",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private int stasis$scaleWeatherParticleTicks(int ticks) {
		return StasisClientState.getPrecipitationBuildTicks(ticks);
	}
}
