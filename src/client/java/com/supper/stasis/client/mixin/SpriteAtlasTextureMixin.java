package com.supper.stasis.client.mixin;

import com.supper.stasis.StasisPhase;
import com.supper.stasis.StasisTimings;
import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes animated textures (fire, end portal, water, lava, etc.) during stasis.
 *
 * During ACTIVE: tick is completely cancelled so animations freeze.
 * During transitions: a tick budget accumulates movementScale each tick.
 * When the budget reaches >= 1.0, one tick is allowed through (advancing
 * the animation by one frame), producing a smooth bullet-time slowdown
 * where animations progressively slow and eventually stop.
 */
@Mixin(SpriteAtlasTexture.class)
public class SpriteAtlasTextureMixin {
	@Unique
	private float stasis$animationBudget = 0.0f;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void stasis$onTick(CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			stasis$animationBudget = 0.0f;
			return;
		}

		if (!StasisClientState.isRunning() || !StasisClientState.affectsWorld(client.world)) {
			stasis$animationBudget = 0.0f;
			return;
		}

		StasisPhase phase = StasisClientState.getPhase();

		if (phase == StasisPhase.ACTIVE) {
			// Complete freeze: cancel tick entirely
			ci.cancel();
			return;
		}

		// Transitions: use tick budget to progressively slow/resume animations
		float movementScale = StasisTimings.getMovementScale(phase, StasisClientState.getRenderProgressFrame());

		stasis$animationBudget += movementScale;

		if (stasis$animationBudget >= 1.0f) {
			// Allow this tick through (animation advances one frame)
			stasis$animationBudget -= 1.0f;
			// Clamp to prevent runaway accumulation
			if (stasis$animationBudget > 1.0f) {
				stasis$animationBudget = 0.0f;
			}
			// Don't cancel — let tick() proceed normally
		} else {
			// Not enough budget: skip this tick (animation stays frozen)
			ci.cancel();
		}
	}
}
