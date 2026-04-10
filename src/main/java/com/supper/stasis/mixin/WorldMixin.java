package com.supper.stasis.mixin;

import com.supper.stasis.StasisManager;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public class WorldMixin {
	@Inject(method = "tickBlockEntities", at = @At("HEAD"), cancellable = true)
	private void stasis$freezeBlockEntities(CallbackInfo ci) {
		World world = (World) (Object) this;
		if (world.isClient()) {
			return;
		}

		if (StasisManager.getInstance().shouldFreezeWorld(world)) {
			ci.cancel();
		}
	}
}
