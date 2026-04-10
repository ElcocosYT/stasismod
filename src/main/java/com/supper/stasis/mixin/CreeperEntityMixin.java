package com.supper.stasis.mixin;

import com.supper.stasis.StasisProgressionHelper;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin {
	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/mob/CreeperEntity;getFuseSpeed()I"
			)
	)
	private int stasis$freezeFuseSpeed(CreeperEntity creeper) {
		if (!StasisProgressionHelper.shouldAdvanceVolatileProgress(creeper)) {
			return 0;
		}

		return creeper.getFuseSpeed();
	}
}
