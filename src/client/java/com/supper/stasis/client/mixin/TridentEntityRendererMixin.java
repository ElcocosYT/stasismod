package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.render.entity.TridentEntityRenderer;
import net.minecraft.client.render.entity.state.TridentEntityRenderState;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TridentEntityRenderer.class)
public class TridentEntityRendererMixin {
	@Inject(
			method = "updateRenderState(Lnet/minecraft/entity/projectile/TridentEntity;Lnet/minecraft/client/render/entity/state/TridentEntityRenderState;F)V",
			at = @At("TAIL")
	)
	private void stasis$alignTridentAnglesWithRenderedPath(
			TridentEntity entity,
			TridentEntityRenderState renderState,
			float tickDelta,
			CallbackInfo ci
	) {
		if (!StasisClientState.isTransitioning() || StasisClientState.isPrivilegedEntity(entity)) {
			return;
		}

		Vec3d step = entity.getEntityPos().subtract(entity.getLastRenderPos());
		if (step.lengthSquared() <= 1.0E-8) {
			return;
		}

		double horizontalLength = Math.sqrt(step.x * step.x + step.z * step.z);
		renderState.yaw = (float) (MathHelper.atan2(step.z, step.x) * 57.2957763671875) - 90.0f;
		renderState.pitch = (float) (MathHelper.atan2(step.y, horizontalLength) * 57.2957763671875);
	}
}
