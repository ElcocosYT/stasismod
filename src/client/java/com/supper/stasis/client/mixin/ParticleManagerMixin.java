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
import net.minecraft.client.particle.ParticleTextureSheet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
	@Shadow
	@Final
	private Map<ParticleTextureSheet, Queue<Particle>> particles;

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
			for (Queue<Particle> queue : this.particles.values()) {
				for (Particle particle : queue) {
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

		for (Queue<Particle> queue : this.particles.values()) {
			for (Particle particle : queue) {
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

	@Unique
	private void stasis$syncAllParticlePositions() {
		for (Queue<Particle> queue : this.particles.values()) {
			for (Particle particle : queue) {
				ParticlePositionAccessor acc = (ParticlePositionAccessor) particle;
				acc.stasis$setPrevPosX(acc.stasis$getX());
				acc.stasis$setPrevPosY(acc.stasis$getY());
				acc.stasis$setPrevPosZ(acc.stasis$getZ());
			}
		}
	}
}
