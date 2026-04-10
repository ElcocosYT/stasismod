package com.supper.stasis.mixin;

import com.supper.stasis.StasisManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityDamageMixin {
	@Inject(method = "damage", at = @At("HEAD"), cancellable = true)
	private void stasis$queueDamageDuringStasis(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		StasisManager manager = StasisManager.getInstance();
		if (manager.shouldBypassDamageCooldown(entity)) {
			manager.resetDamageCooldown(entity);
		}

		if (amount <= 0.0f) {
			return;
		}

		if (manager.shouldIgnoreDamageForActivatingPlayer(entity, source)) {
			cir.setReturnValue(false);
			return;
		}

		if (manager.shouldQueueActivatingPlayerDamage(entity, source)) {
			manager.queueActivatingPlayerDamage(world, entity, source, amount);
			cir.setReturnValue(true);
			return;
		}

		if (manager.shouldQueueDamage(entity)) {
			if (manager.isEntityDamage(source)) {
				manager.queueHit(world, entity, source, amount);
			} else {
				manager.queueNonEntityDamage(world, entity, source, amount);
			}
			cir.setReturnValue(true);
		}
	}
}
