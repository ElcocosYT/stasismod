package com.supper.stasis.client.compat;

/**
 * Stub compatibility layer for Iris. The actual depth occlusion fix for trails
 * visible through walls with shaders active is handled in PlayerTrailRenderer
 * by copying the depth buffer during the world render pass (before Iris
 * modifies/clears it), matching the pattern used in 1.21.1.
 *
 * This class is kept as a no-op stub so any lingering references compile
 * cleanly. All Iris-specific reflection hooks have been removed because they
 * were error-prone and unnecessary — the vanilla depth buffer captured at the
 * right time already contains the correct scene geometry.
 */
public final class IrisRenderPipelineCompat {
	private IrisRenderPipelineCompat() {
	}
}
