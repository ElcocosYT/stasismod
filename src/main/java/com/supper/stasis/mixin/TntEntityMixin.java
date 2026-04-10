package com.supper.stasis.mixin;

import com.supper.stasis.StasisProgressionHelper;
import net.minecraft.entity.TntEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TntEntity.class)
public abstract class TntEntityMixin {
	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/TntEntity;getFuse()I"
			)
	)
	private int stasis$freezeFuseCountdown(TntEntity tnt) {
		int fuse = tnt.getFuse();
		if (fuse > 0 && !StasisProgressionHelper.shouldAdvanceVolatileProgress(tnt)) {
			return fuse + 1;
		}

		return fuse;
	}

	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
			)
	)
	private void stasis$suppressFuseSmoke(
				World world,
				ParticleEffect particle,
				double x,
				double y,
				double z,
				double velocityX,
				double velocityY,
				double velocityZ
	) {
		TntEntity tnt = (TntEntity) (Object) this;
		if (!StasisProgressionHelper.shouldAdvanceVolatileProgress(tnt) && tnt.getFuse() > 0) {
			return;
		}

		world.addParticleClient(particle, x, y, z, velocityX, velocityY, velocityZ);
	}
}
