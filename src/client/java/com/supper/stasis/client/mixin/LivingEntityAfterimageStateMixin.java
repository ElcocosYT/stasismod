package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityAfterimageStateMixin {
	@Inject(method = "isUsingItem", at = @At("HEAD"), cancellable = true)
	private void stasis$useAfterimageUsingItem(CallbackInfoReturnable<Boolean> cir) {
		Boolean usingItem = AfterimageRenderState.getUsingItemOverride();
		if (usingItem != null) {
			cir.setReturnValue(usingItem);
		}
	}

	@Inject(method = "getActiveHand", at = @At("HEAD"), cancellable = true)
	private void stasis$useAfterimageActiveHand(CallbackInfoReturnable<Hand> cir) {
		Hand activeHand = AfterimageRenderState.getActiveHandOverride();
		if (activeHand != null) {
			cir.setReturnValue(activeHand);
		}
	}

	@Inject(method = "getItemUseTimeLeft", at = @At("HEAD"), cancellable = true)
	private void stasis$useAfterimageItemUseTimeLeft(CallbackInfoReturnable<Integer> cir) {
		Integer itemUseTimeLeft = AfterimageRenderState.getItemUseTimeLeftOverride();
		if (itemUseTimeLeft != null) {
			cir.setReturnValue(itemUseTimeLeft);
		}
	}
}
