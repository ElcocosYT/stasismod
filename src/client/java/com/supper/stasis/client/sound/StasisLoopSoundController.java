package com.supper.stasis.client.sound;

import com.supper.stasis.Stasis;
import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.MinecraftClient;
import com.supper.stasis.StasisPhase;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;

public final class StasisLoopSoundController {
	private static StasisLoopSoundInstance currentSound;
	private static StasisLoopSoundInstance queuedSound;
	private static StasisPhase lastPhase = StasisPhase.IDLE;
	private static boolean resumePending = false;

	private StasisLoopSoundController() {
	}

	public static void tick(MinecraftClient client) {
		if (client.world == null || !StasisClientState.affectsWorld(client.world)) {
			stop(client);
			return;
		}

		StasisPhase phase = StasisClientState.getPhase();
		SoundManager soundManager = client.getSoundManager();

		if (currentSound != null && currentSound.isDone()) {
			currentSound = queuedSound;
			queuedSound = null;
		}

		if (phase == StasisPhase.IDLE || phase == StasisPhase.TRANSITION_IN) {
			stop(client);
			lastPhase = phase;
			return;
		}

		if (currentSound != null && !soundManager.isPlaying(currentSound)) {
			currentSound = null;
		}

		if (queuedSound != null && queuedSound.isDone()) {
			queuedSound = null;
		}

		if (queuedSound != null && !soundManager.isPlaying(queuedSound)) {
			queuedSound = null;
		}

		if (currentSound == null && queuedSound != null) {
			currentSound = queuedSound;
			queuedSound = null;
		}

		if (phase == StasisPhase.TRANSITION_OUT && lastPhase != StasisPhase.TRANSITION_OUT) {
			resumePending = true;
			if (queuedSound != null) {
				if (currentSound != null) {
					soundManager.stop(currentSound);
				}
				currentSound = queuedSound;
				queuedSound = null;
			}

			if (currentSound != null) {
				currentSound.finishCurrentLoop();
			}
		}

		if (currentSound == null && phase == StasisPhase.ACTIVE) {
			currentSound = new StasisLoopSoundInstance();
			soundManager.play(currentSound);
		}

		if (phase == StasisPhase.ACTIVE && currentSound != null && queuedSound == null && currentSound.shouldQueueSuccessor()) {
			queuedSound = new StasisLoopSoundInstance(true);
			soundManager.play(queuedSound);
		}

		if (phase == StasisPhase.TRANSITION_OUT && queuedSound != null) {
			soundManager.stop(queuedSound);
			queuedSound = null;
		}

		if (phase == StasisPhase.TRANSITION_OUT && resumePending && (currentSound == null || !soundManager.isPlaying(currentSound))) {
			soundManager.play(PositionedSoundInstance.master(Stasis.TIMESTOP_RESUME_SOUND, 1.0f, 1.0f));
			resumePending = false;
		}

		lastPhase = phase;
	}

	public static void stop(MinecraftClient client) {
		resumePending = false;
		lastPhase = StasisPhase.IDLE;
		if (currentSound == null) {
			if (queuedSound != null) {
				client.getSoundManager().stop(queuedSound);
				queuedSound = null;
			}
			return;
		}

		client.getSoundManager().stop(currentSound);
		if (queuedSound != null) {
			client.getSoundManager().stop(queuedSound);
			queuedSound = null;
		}
		currentSound = null;
	}
}
