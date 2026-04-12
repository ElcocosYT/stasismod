package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import com.supper.stasis.client.render.AfterimageTintingVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemRenderer.class)
public class ItemRendererAfterimageMixin {
	private static final Identifier STASIS_ITEM_ATLAS_TEXTURE = Identifier.ofVanilla("textures/atlas/blocks.png");

	@Redirect(
			method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/RenderLayers;getItemLayer(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/client/render/RenderLayer;"
			)
	)
	private RenderLayer stasis$useAfterimageItemLayer(ItemStack stack, boolean solid) {
		return AfterimageRenderState.isActive()
				? RenderLayer.getItemEntityTranslucentCull(STASIS_ITEM_ATLAS_TEXTURE)
				: RenderLayers.getItemLayer(stack, solid);
	}

	@Redirect(
			method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/item/ItemRenderer;usesDynamicDisplay(Lnet/minecraft/item/ItemStack;)Z"
			)
	)
	private boolean stasis$disableDynamicDisplay(ItemStack stack) {
		return !AfterimageRenderState.isActive() && ItemRendererAfterimageMixin.usesDynamicDisplay(stack);
	}

	@ModifyArg(
			method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/item/ItemRenderer;getDirectItemGlintConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/RenderLayer;ZZ)Lnet/minecraft/client/render/VertexConsumer;"
			),
			index = 3
	)
	private boolean stasis$disableDirectGlint(boolean hasGlint) {
		return AfterimageRenderState.isActive() ? false : hasGlint;
	}

	@ModifyArg(
			method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/item/ItemRenderer;getItemGlintConsumer(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/RenderLayer;ZZ)Lnet/minecraft/client/render/VertexConsumer;"
			),
			index = 3
	)
	private boolean stasis$disableItemGlint(boolean hasGlint) {
		return AfterimageRenderState.isActive() ? false : hasGlint;
	}

	@ModifyVariable(
			method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0
	)
	private VertexConsumerProvider stasis$wrapAfterimageItemVertexConsumers(VertexConsumerProvider vertexConsumers) {
		return AfterimageTintingVertexConsumerProvider.wrap(vertexConsumers);
	}

	private static boolean usesDynamicDisplay(ItemStack stack) {
		return stack.isIn(ItemTags.COMPASSES) || stack.isOf(Items.CLOCK);
	}
}
