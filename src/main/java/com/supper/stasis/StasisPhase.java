package com.supper.stasis;

public enum StasisPhase {
	IDLE(0),
	TRANSITION_IN(1),
	ACTIVE(2),
	TRANSITION_OUT(3);

	private final int networkId;

	StasisPhase(int networkId) {
		this.networkId = networkId;
	}

	public int getNetworkId() {
		return networkId;
	}

	public boolean isRunning() {
		return this != IDLE;
	}

	public boolean isTransition() {
		return this == TRANSITION_IN || this == TRANSITION_OUT;
	}

	public static StasisPhase fromNetworkId(int networkId) {
		for (StasisPhase phase : values()) {
			if (phase.networkId == networkId) {
				return phase;
			}
		}

		return IDLE;
	}
}
