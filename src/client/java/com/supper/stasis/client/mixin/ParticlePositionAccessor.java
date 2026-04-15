package com.supper.stasis.client.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Provides access to Particle position fields for scaling movement and syncing
 * lastPos = pos when freezing particles during stasis.
 * Note: In 1.21.11, prevPosX/Y/Z were renamed to lastX/Y/Z.
 */
@Mixin(Particle.class)
public interface ParticlePositionAccessor {
	@Accessor("lastX")
	void stasis$setPrevPosX(double value);

	@Accessor("lastY")
	void stasis$setPrevPosY(double value);

	@Accessor("lastZ")
	void stasis$setPrevPosZ(double value);

	@Accessor("x")
	double stasis$getX();

	@Accessor("y")
	double stasis$getY();

	@Accessor("z")
	double stasis$getZ();

	@Accessor("x")
	void stasis$setX(double value);

	@Accessor("y")
	void stasis$setY(double value);

	@Accessor("z")
	void stasis$setZ(double value);
}
