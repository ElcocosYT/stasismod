package com.supper.stasis.client.mixin;

import net.minecraft.client.network.ClientPlayerLikeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerLikeState.class)
public interface ClientPlayerLikeStateAccessor {
	@Accessor("x")
	double stasis$getX();

	@Accessor("x")
	void stasis$setX(double x);

	@Accessor("y")
	double stasis$getY();

	@Accessor("y")
	void stasis$setY(double y);

	@Accessor("z")
	double stasis$getZ();

	@Accessor("z")
	void stasis$setZ(double z);

	@Accessor("lastX")
	double stasis$getLastX();

	@Accessor("lastX")
	void stasis$setLastX(double lastX);

	@Accessor("lastY")
	double stasis$getLastY();

	@Accessor("lastY")
	void stasis$setLastY(double lastY);

	@Accessor("lastZ")
	double stasis$getLastZ();

	@Accessor("lastZ")
	void stasis$setLastZ(double lastZ);

	@Accessor("movement")
	float stasis$getMovement();

	@Accessor("movement")
	void stasis$setMovement(float movement);

	@Accessor("lastMovement")
	float stasis$getLastMovement();

	@Accessor("lastMovement")
	void stasis$setLastMovement(float lastMovement);

	@Accessor("distanceMoved")
	float stasis$getDistanceMoved();

	@Accessor("distanceMoved")
	void stasis$setDistanceMoved(float distanceMoved);

	@Accessor("lastDistanceMoved")
	float stasis$getLastDistanceMoved();

	@Accessor("lastDistanceMoved")
	void stasis$setLastDistanceMoved(float lastDistanceMoved);
}
