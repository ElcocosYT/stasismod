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
	private void stasis$queueDamageDuringStasis(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity.getWorld().isClient) {
			return;
		}

		StasisManager manager = StasisManager.getInstance();
		if (manager.shouldBypassDamageCooldown(entity)) {
			manager.resetDamageCooldown(entity);
		}

		if (!(entity.getWorld() instanceof ServerWorld serverWorld) || amount <= 0.0f) {
			return;
		}

		if (manager.shouldIgnoreDamageForActivatingPlayer(entity, source)) {
			cir.setReturnValue(false);
			return;
		}

		if (manager.shouldQueueActivatingPlayerDamage(entity, source)) {
			manager.queueActivatingPlayerDamage(serverWorld, entity, source, amount);
			cir.setReturnValue(true);
			return;
		}

		if (manager.shouldQueueDamage(entity)) {
			if (manager.isEntityDamage(source)) {
				manager.queueHit(serverWorld, entity, source, amount);
			} else {
				manager.queueNonEntityDamage(serverWorld, entity, source, amount);
			}
			cir.setReturnValue(true);
		}
	}
}
