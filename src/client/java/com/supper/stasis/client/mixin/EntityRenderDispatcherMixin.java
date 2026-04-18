package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderManager.class)
public class EntityRenderDispatcherMixin {
	@ModifyVariable(method = "getAndUpdateRenderState", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float stasis$freezeEntityRenderTickDelta(float tickDelta, Entity entity) {
		if (!(entity.getEntityWorld() instanceof ClientWorld clientWorld)) {
			return tickDelta;
		}

		if (!StasisClientState.affectsWorld(clientWorld)) {
			return tickDelta;
		}

		if (StasisClientState.isPrivilegedEntity(entity)) {
			return tickDelta;
		}

		// Non-living entities (projectiles, items, etc.) are already transition-scaled in
		// their physical tick updates, so they MUST use the unscaled tickDelta to
		// correctly interpolate between the scaled physical steps.
		if (!(entity instanceof LivingEntity)) {
			return tickDelta;
		}

		return StasisClientState.getEntityRenderTickDelta(entity, tickDelta);
	}
}
