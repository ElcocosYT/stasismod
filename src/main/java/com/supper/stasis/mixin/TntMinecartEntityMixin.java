package com.supper.stasis.mixin;

import com.supper.stasis.StasisProgressionHelper;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TntMinecartEntity.class)
public abstract class TntMinecartEntityMixin {
	@Shadow
	private int fuseTicks;

	@Redirect(
			method = "tick",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/entity/vehicle/TntMinecartEntity;fuseTicks:I",
					opcode = Opcodes.PUTFIELD
			)
	)
	private void stasis$freezeFuseWrite(TntMinecartEntity minecart, int fuseTicks) {
		if (!StasisProgressionHelper.shouldAdvanceVolatileProgress(minecart) && this.fuseTicks > 0) {
			return;
		}

		this.fuseTicks = fuseTicks;
	}

	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V"
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
		TntMinecartEntity minecart = (TntMinecartEntity) (Object) this;
		if (!StasisProgressionHelper.shouldAdvanceVolatileProgress(minecart) && this.fuseTicks > 0) {
			return;
		}

		world.addParticle(particle, x, y, z, velocityX, velocityY, velocityZ);
	}
}
