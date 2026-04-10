package com.supper.stasis;

public final class StasisTimings {
	public static final double STOP_SOUND_SECONDS = 7.151746;
	public static final double STASIS_LOOP_SECONDS = 4.048980;
	public static final double RESUME_SOUND_SECONDS = 3.456000;

	public static final int STASIS_LOOP_TICKS = secondsToTicks(STASIS_LOOP_SECONDS);

	public static final float MIN_WORLD_SOUND_PITCH = 0.45f;

	private StasisTimings() {
	}

	public static float getFreezeScale(float progress) {
		return 1.0f - smootherstep(progress);
	}

	public static float getReleaseScale(float releaseProgress) {
		return smootherstep(releaseProgress);
	}

	public static float getMovementScale(StasisPhase phase, float progress) {
		float clampedProgress = clamp01(progress);
		return switch (phase) {
			case IDLE -> 1.0f;
			case ACTIVE -> 0.0f;
			case TRANSITION_IN -> getFreezeScale(clampedProgress);
			case TRANSITION_OUT -> getReleaseScale(1.0f - clampedProgress);
		};
	}

	public static int getTransitionTicks(StasisPhase phase) {
		return switch (phase) {
			case TRANSITION_IN -> getTransitionInTicks();
			case TRANSITION_OUT -> getTransitionOutTicks();
			case IDLE, ACTIVE -> 0;
		};
	}

	public static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	public static float smootherstep(float value) {
		float t = clamp01(value);
		return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
	}

	public static int getTransitionInTicks() {
		return secondsToTicks(STOP_SOUND_SECONDS);
	}

	public static int getTransitionOutTicks() {
		return secondsToTicks(RESUME_SOUND_SECONDS);
	}

	private static int secondsToTicks(double seconds) {
		return Math.max(1, (int) Math.round(seconds * 20.0));
	}
}
