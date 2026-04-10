package com.supper.stasis.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.supper.stasis.client.StasisClientState;
import com.supper.stasis.client.render.StasisShaderManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Unique
	private boolean stasis$shaderAppliedThisFrame;

	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void stasis$resetShaderFlag(RenderTickCounter tickCounter, CallbackInfo ci) {
		this.stasis$shaderAppliedThisFrame = false;
		StasisClientState.updateRenderProgress(tickCounter.getTickProgress(false));
	}

	@Inject(
			method = "renderWorld",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/GameRenderer;renderHand(FZLorg/joml/Matrix4f;)V"
			)
	)
	private void stasis$applyStasisShaderBeforeHand(RenderTickCounter tickCounter, CallbackInfo ci) {
		GameRenderer renderer = (GameRenderer) (Object) this;
		stasis$renderShader(renderer);
		stasis$restoreHandRenderState(renderer);
		this.stasis$shaderAppliedThisFrame = true;
	}

	@Inject(method = "renderWorld", at = @At("TAIL"))
	private void stasis$applyStasisShaderFallback(RenderTickCounter tickCounter, CallbackInfo ci) {
		if (this.stasis$shaderAppliedThisFrame) {
			return;
		}

		GameRenderer renderer = (GameRenderer) (Object) this;
		stasis$renderShader(renderer);
		stasis$restoreHandRenderState(renderer);
	}

	@Unique
	private void stasis$renderShader(GameRenderer renderer) {
		boolean shouldRender = renderer.getClient().world != null
				&& StasisClientState.isRunning()
				&& StasisClientState.affectsWorld(renderer.getClient().world);

		if (!shouldRender) {
			StasisShaderManager.cleanup();
			return;
		}

		StasisShaderManager.ensureLoaded(renderer);
		StasisShaderManager.render(renderer, StasisClientState.getProgress());
	}

	@Unique
	private void stasis$restoreHandRenderState(GameRenderer renderer) {
		if (renderer.getClient().getFramebuffer().getDepthAttachment() != null) {
			RenderSystem.getDevice()
					.createCommandEncoder()
					.clearDepthTexture(renderer.getClient().getFramebuffer().getDepthAttachment(), 1.0);
		}
	}
}
