package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderState.LayerRenderState.class)
public abstract class ItemLayerRenderStateAfterimageMixin {
	private static final Identifier STASIS_FALLBACK_ATLAS_TEXTURE = Identifier.ofVanilla("textures/atlas/blocks.png");

	@Shadow
	private RenderLayer renderLayer;

	@Shadow
	private ItemRenderState.Glint glint;

	@Shadow
	Sprite particle;

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
		Identifier atlasTexture = this.particle != null ? this.particle.getAtlasId() : STASIS_FALLBACK_ATLAS_TEXTURE;
		this.setRenderLayer(RenderLayers.itemEntityTranslucentCull(atlasTexture));
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
					target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V"
			),
			index = 5
	)
	private int[] stasis$applyQueuedAfterimageItemTints(int[] tints) {
		return AfterimageRenderState.isActive() ? new int[]{AfterimageRenderState.getColor()} : tints;
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/item/ItemRenderState$Glint;)V"
			),
			index = 6
	)
	private List<BakedQuad> stasis$applyQueuedAfterimageItemQuads(List<BakedQuad> quads) {
		return AfterimageRenderState.isActive() ? stasis$createAfterimageQuads(quads) : quads;
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

	@Unique
	private static List<BakedQuad> stasis$createAfterimageQuads(List<BakedQuad> quads) {
		if (quads == null || quads.isEmpty()) {
			return quads;
		}

		List<BakedQuad> tintedQuads = new ArrayList<>(quads.size());
		for (BakedQuad quad : quads) {
			tintedQuads.add(new BakedQuad(
					quad.position0(),
					quad.position1(),
					quad.position2(),
					quad.position3(),
					quad.packedUV0(),
					quad.packedUV1(),
					quad.packedUV2(),
					quad.packedUV3(),
					0,
					quad.face(),
					quad.sprite(),
					quad.shade(),
					quad.lightEmission()
			));
		}
		return tintedQuads;
	}
}
