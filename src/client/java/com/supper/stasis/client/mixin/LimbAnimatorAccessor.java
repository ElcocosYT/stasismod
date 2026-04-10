package com.supper.stasis.client.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
	@Accessor("lastSpeed")
	float stasis$getPrevSpeed();

	@Accessor("lastSpeed")
	void stasis$setPrevSpeed(float prevSpeed);

	@Accessor("speed")
	float stasis$getSpeed();

	@Accessor("speed")
	void stasis$setSpeed(float speed);

	@Accessor("animationProgress")
	float stasis$getPos();

	@Accessor("animationProgress")
	void stasis$setPos(float pos);
}
