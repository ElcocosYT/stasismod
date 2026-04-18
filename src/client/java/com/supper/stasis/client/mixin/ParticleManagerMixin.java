package com.supper.stasis.client.mixin;

import com.supper.stasis.StasisPhase;
import com.supper.stasis.StasisTimings;
import com.supper.stasis.client.StasisClientState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleRenderer;
import net.minecraft.client.particle.ParticleTextureSheet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes all non-weather particles during stasis ACTIVE phase and smoothly
 * scales their movement during transitions for a bullet-time effect.
 *
 * 1.21.11 uses ParticleRenderer wrappers around the particle queues.
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
	@Shadow
	@Final
	private Map<ParticleTextureSheet, ParticleRenderer<?>> particles;

	@Unique
	private float stasis$currentMovementScale = 1.0f;

	@Unique
	private final IdentityHashMap<Particle, double[]> stasis$savedPositions = new IdentityHashMap<>();

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void stasis$beforeTick(CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			stasis$currentMovementScale = 1.0f;
			return;
		}

		if (!StasisClientState.isRunning() || !StasisClientState.affectsWorld(client.world)) {
			stasis$currentMovementScale = 1.0f;
			return;
		}

		StasisPhase phase = StasisClientState.getPhase();

		if (phase == StasisPhase.ACTIVE) {
			stasis$syncAllParticlePositions();
			stasis$currentMovementScale = 1.0f;
			ci.cancel();
			return;
		}

		float movementScale = StasisTimings.getMovementScale(phase, StasisClientState.getRenderProgressFrame());
		stasis$currentMovementScale = movementScale;

		if (movementScale < 0.9999f) {
			stasis$savedPositions.clear();
			for (ParticleRenderer<?> renderer : this.particles.values()) {
				for (Particle particle : renderer.getParticles()) {
					ParticlePositionAccessor acc = (ParticlePositionAccessor) particle;
					stasis$savedPositions.put(particle, new double[]{
							acc.stasis$getX(), acc.stasis$getY(), acc.stasis$getZ()
					});
				}
			}
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void stasis$afterTick(CallbackInfo ci) {
		if (stasis$currentMovementScale >= 0.9999f || stasis$savedPositions.isEmpty()) {
			stasis$currentMovementScale = 1.0f;
			return;
		}

		float scale = stasis$currentMovementScale;

		for (ParticleRenderer<?> renderer : this.particles.values()) {
			for (Particle particle : renderer.getParticles()) {
				double[] oldPos = stasis$savedPositions.get(particle);
				if (oldPos == null) {
					continue;
				}

				ParticlePositionAccessor acc = (ParticlePositionAccessor) particle;
				double fullMoveX = acc.stasis$getX() - oldPos[0];
				double fullMoveY = acc.stasis$getY() - oldPos[1];
				double fullMoveZ = acc.stasis$getZ() - oldPos[2];

				acc.stasis$setX(oldPos[0] + fullMoveX * scale);
				acc.stasis$setY(oldPos[1] + fullMoveY * scale);
				acc.stasis$setZ(oldPos[2] + fullMoveZ * scale);

				acc.stasis$setPrevPosX(oldPos[0]);
				acc.stasis$setPrevPosY(oldPos[1]);
				acc.stasis$setPrevPosZ(oldPos[2]);
			}
		}

		stasis$savedPositions.clear();
		stasis$currentMovementScale = 1.0f;
	}

	/**
	 * Sets prevPos = pos for every particle so that rendering interpolation
	 * produces a constant position regardless of tickDelta (perfect freeze).
	 */
	@Unique
	private void stasis$syncAllParticlePositions() {
		for (ParticleRenderer<?> renderer : this.particles.values()) {
			for (Particle particle : renderer.getParticles()) {
				ParticlePositionAccessor acc = (ParticlePositionAccessor) particle;
				acc.stasis$setPrevPosX(acc.stasis$getX());
				acc.stasis$setPrevPosY(acc.stasis$getY());
				acc.stasis$setPrevPosZ(acc.stasis$getZ());
			}
		}
	}

	@org.spongepowered.asm.mixin.injection.ModifyVariable(
			method = "renderParticles*",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private float stasis$freezeParticleRenderTickDelta(float tickDelta) {
		// Zeroing out the sub-tick interpolation completely stops particle sizes (e.g. fire/explosions)
		// and rotations from jittering forward and backward when the game is completely paused.
		if (StasisClientState.getPhase() == StasisPhase.ACTIVE) {
			return 0.0f;
		}
		return tickDelta;
	}
}
