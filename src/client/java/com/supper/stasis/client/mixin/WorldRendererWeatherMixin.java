package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

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
		return this.stasis$weatherTickProgress;
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
}
