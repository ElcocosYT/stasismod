package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class ClientEntityLookMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		Entity entity = (Entity) (Object) this;
		if (!(entity.getEntityWorld() instanceof ClientWorld clientWorld)) {
			return;
		}

		if (!StasisClientState.isActive() || !StasisClientState.affectsWorld(clientWorld)) {
			return;
		}

		if (StasisClientState.isPrivilegedEntity(entity)) {
			return;
		}

		ci.cancel();
	}
}
