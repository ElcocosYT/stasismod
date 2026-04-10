package com.supper.stasis.client.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {
	@Accessor("prevSpeed")
	float stasis$getPrevSpeed();

	@Accessor("prevSpeed")
	void stasis$setPrevSpeed(float prevSpeed);

	@Accessor("speed")
	float stasis$getSpeed();

	@Accessor("speed")
	void stasis$setSpeed(float speed);

	@Accessor("pos")
	float stasis$getPos();

	@Accessor("pos")
	void stasis$setPos(float pos);
}
