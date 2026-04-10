package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
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

		// Non-living entities already have their position and velocity transition-scaled in
		// the world tick. Re-scaling render tickDelta here makes projectiles visually sag and
		// snap between sub-steps instead of following the original smooth bullet-time arc.
		if (!(entity instanceof LivingEntity) && StasisClientState.isTransitioning()) {
			return tickDelta;
		}

		return StasisClientState.getEntityRenderTickDelta(entity, tickDelta);
	}
}
