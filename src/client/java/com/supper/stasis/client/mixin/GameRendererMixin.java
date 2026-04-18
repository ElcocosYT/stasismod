package com.supper.stasis.client.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import com.supper.stasis.client.StasisClientState;
import com.supper.stasis.client.render.PlayerTrailRenderer;
import com.supper.stasis.client.render.ShockwaveRenderer;
import com.supper.stasis.client.render.StasisShaderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Shadow
	private Camera camera;

	@Unique
	private boolean stasis$shaderAppliedThisFrame;

	@Inject(method = "renderWorld", at = @At("HEAD"))
	private void stasis$resetShaderFlag(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
		this.stasis$shaderAppliedThisFrame = false;
		PlayerTrailRenderer.beginRenderFrame();
		StasisClientState.updateRenderProgress(tickDelta);
	}

	@Inject(
			method = "renderWorld",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/GameRenderer;renderHand(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/Camera;F)V"
			)
	)
	private void stasis$applyStasisShaderBeforeHand(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
		GameRenderer renderer = (GameRenderer) (Object) this;
		PlayerTrailRenderer.captureTrailFramebufferPostWorld(this.camera);
		stasis$renderShader(renderer, tickDelta);
		ShockwaveRenderer.render(renderer, tickDelta);
		PlayerTrailRenderer.renderPostShader(this.camera);
		stasis$restoreHandRenderState(renderer);
		this.stasis$shaderAppliedThisFrame = true;
	}

	@Inject(method = "renderWorld", at = @At("TAIL"))
	private void stasis$applyStasisShaderFallback(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
		if (this.stasis$shaderAppliedThisFrame) {
			return;
		}

		PlayerTrailRenderer.captureTrailFramebufferPostWorld(this.camera);
		stasis$renderShader((GameRenderer) (Object) this, tickDelta);
		ShockwaveRenderer.render((GameRenderer) (Object) this, tickDelta);
		PlayerTrailRenderer.renderPostShader(this.camera);
	}

	@Unique
	private void stasis$renderShader(GameRenderer renderer, float tickDelta) {
		boolean shouldRender = renderer.getClient().world != null
				&& StasisClientState.isRunning()
				&& StasisClientState.affectsWorld(renderer.getClient().world);

		if (!shouldRender) {
			StasisShaderManager.cleanup();
			return;
		}

		StasisShaderManager.ensureLoaded(renderer);
		if (StasisShaderManager.getShader() != null) {
			StasisShaderManager.render(renderer, tickDelta, StasisClientState.getProgress());
		}
	}

	@Unique
	private void stasis$restoreHandRenderState(GameRenderer renderer) {
		renderer.getClient().getFramebuffer().beginWrite(false);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
	}
}
