package com.supper.stasis.client.sound;

import com.supper.stasis.Stasis;
import com.supper.stasis.StasisPhase;
import com.supper.stasis.StasisTimings;
import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;

public final class StasisLoopSoundInstance extends MovingSoundInstance {
	public static final int SEAMLESS_OVERLAP_TICKS = 2;

	private final boolean seamlessSuccessor;
	private boolean finishingCurrentLoop = false;
	private int ageTicks = 0;

	public StasisLoopSoundInstance() {
		this(false);
	}

	public StasisLoopSoundInstance(boolean seamlessSuccessor) {
		super(Stasis.TIMESTOP_STASIS_SOUND, SoundCategory.PLAYERS, SoundInstance.createRandom());
		this.seamlessSuccessor = seamlessSuccessor;
		this.repeat = false;
		this.repeatDelay = 0;
		this.relative = true;
		this.attenuationType = SoundInstance.AttenuationType.NONE;
		this.volume = 1.0f;
		this.pitch = 1.0f;
		this.x = 0.0;
		this.y = 0.0;
		this.z = 0.0;
	}

	@Override
	public boolean shouldAlwaysPlay() {
		return true;
	}

	@Override
	public boolean canPlay() {
		return true;
	}

	@Override
	public void tick() {
		StasisPhase phase = StasisClientState.getPhase();
		this.ageTicks++;
		if (phase == StasisPhase.IDLE || phase == StasisPhase.TRANSITION_IN) {
			this.volume = 0.0f;
			this.setDone();
			return;
		}

		if (phase == StasisPhase.TRANSITION_OUT && !this.finishingCurrentLoop) {
			this.volume = 0.0f;
			this.setDone();
			return;
		}

		float baseVolume = 1.0f;
		this.volume = baseVolume * getLoopEnvelope();

		if (this.ageTicks >= StasisTimings.STASIS_LOOP_TICKS) {
			this.volume = 0.0f;
			this.setDone();
		}
	}

	public boolean shouldQueueSuccessor() {
		return this.ageTicks >= StasisTimings.STASIS_LOOP_TICKS - SEAMLESS_OVERLAP_TICKS;
	}

	public void finishCurrentLoop() {
		this.finishingCurrentLoop = true;
	}

	private float getLoopEnvelope() {
		float fadeIn = 1.0f;
		if (this.seamlessSuccessor && this.ageTicks < SEAMLESS_OVERLAP_TICKS) {
			fadeIn = this.ageTicks / (float) SEAMLESS_OVERLAP_TICKS;
		}

		int ticksRemaining = StasisTimings.STASIS_LOOP_TICKS - this.ageTicks;
		float fadeOut = ticksRemaining < SEAMLESS_OVERLAP_TICKS
				? Math.max(0.0f, ticksRemaining / (float) SEAMLESS_OVERLAP_TICKS)
				: 1.0f;
		return Math.min(fadeIn, fadeOut);
	}
}
