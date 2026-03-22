package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(net.minecraft.client.render.entity.EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Shadow
	private World world;

	@ModifyVariable(method = "render", at = @At("HEAD"), ordinal = 1)
	private float stasis$freezeEntityRenderTickDelta(
			float tickDelta,
			Entity entity,
			double x,
			double y,
			double z,
			float yaw,
			float originalTickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		if (!(this.world instanceof ClientWorld clientWorld)) {
			return tickDelta;
		}

		if (!StasisClientState.affectsWorld(clientWorld)) {
			return tickDelta;
		}

		if (StasisClientState.isPrivilegedEntity(entity)) {
			return tickDelta;
		}

		// LivingEntity animations are slowed in LivingEntityRendererMixin; keep dispatcher
		// tickDelta spatial so body translation and shadow stay perfectly aligned.
		if (entity instanceof LivingEntity) {
			return tickDelta;
		}

		// Non-living entities are already transition-scaled in world tick state.
		if (StasisClientState.isTransitioning()) {
			return tickDelta;
		}

		return StasisClientState.getEntityRenderTickDelta(entity, tickDelta);
	}
}
