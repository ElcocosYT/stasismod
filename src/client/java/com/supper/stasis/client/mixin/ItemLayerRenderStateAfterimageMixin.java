package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderState.LayerRenderState.class)
public abstract class ItemLayerRenderStateAfterimageMixin {
	private static final Identifier STASIS_ITEM_ATLAS_TEXTURE = Identifier.ofVanilla("textures/atlas/items.png");

	@Shadow
	private RenderLayer renderLayer;

	@Shadow
	private ItemRenderState.Glint glint;

	@Shadow
	public abstract void setRenderLayer(RenderLayer renderLayer);

	@Shadow
	public abstract void setGlint(ItemRenderState.Glint glint);

	@Unique
	private RenderLayer stasis$originalRenderLayer;

	@Unique
	private ItemRenderState.Glint stasis$originalGlint;

	@Unique
	private boolean stasis$afterimageOverrideApplied;

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V", at = @At("HEAD"))
	private void stasis$applyAfterimageRenderState(MatrixStack matrices, OrderedRenderCommandQueue commandQueue, int light, int overlay, int outline, CallbackInfo ci) {
		if (!AfterimageRenderState.isActive()) {
			this.stasis$afterimageOverrideApplied = false;
			return;
		}

		this.stasis$originalRenderLayer = this.renderLayer;
		this.stasis$originalGlint = this.glint;
		this.stasis$afterimageOverrideApplied = true;
		this.setRenderLayer(RenderLayers.itemEntityTranslucentCull(STASIS_ITEM_ATLAS_TEXTURE));
		this.setGlint(ItemRenderState.Glint.NONE);
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V", at = @At("RETURN"))
	private void stasis$restoreAfterimageRenderState(MatrixStack matrices, OrderedRenderCommandQueue commandQueue, int light, int overlay, int outline, CallbackInfo ci) {
		if (!this.stasis$afterimageOverrideApplied) {
			return;
		}

		this.renderLayer = this.stasis$originalRenderLayer;
		this.glint = this.stasis$originalGlint;
		this.stasis$afterimageOverrideApplied = false;
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/item/model/special/SpecialModelRenderer;render(Ljava/lang/Object;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IIZI)V"
			),
			index = 6
	)
	private boolean stasis$disableSpecialModelGlint(boolean hasGlint) {
		return AfterimageRenderState.isActive() ? false : hasGlint;
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/item/model/special/SpecialModelRenderer;render(Ljava/lang/Object;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IIZI)V"
			),
			index = 7
	)
	private int stasis$applySpecialModelAfterimageColor(int color) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getColor() : color;
	}
}
