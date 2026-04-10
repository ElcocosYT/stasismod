package com.supper.stasis.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("hurtTime")
	void stasis$setHurtTime(int value);

	@Accessor("maxHurtTime")
	void stasis$setMaxHurtTime(int value);
}
