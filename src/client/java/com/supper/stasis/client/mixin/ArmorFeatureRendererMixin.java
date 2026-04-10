package com.supper.stasis.client.mixin;

import com.supper.stasis.client.render.AfterimageRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EquipmentRenderer.class)
public class ArmorFeatureRendererMixin {
	@Redirect(
			method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/RenderLayers;armorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
			)
	)
	private static RenderLayer stasis$useAfterimageArmorLayer(Identifier texture) {
		return AfterimageRenderState.isActive()
				? RenderLayers.entityTranslucent(texture)
				: RenderLayers.armorCutoutNoCull(texture);
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V"
			),
			index = 6
	)
	private static int stasis$applyAfterimageArmorColor(int color) {
		return AfterimageRenderState.isActive() ? AfterimageRenderState.getColor() : color;
	}

	@Redirect(
			method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/item/ItemStack;hasGlint()Z"
			)
	)
	private static boolean stasis$disableArmorGlint(ItemStack stack) {
		return !AfterimageRenderState.isActive() && stack.hasGlint();
	}

	@Redirect(
			method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/util/Identifier;II)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/item/ItemStack;get(Lnet/minecraft/component/ComponentType;)Ljava/lang/Object;"
			)
	)
	private static Object stasis$skipArmorTrim(ItemStack stack, ComponentType<?> componentType) {
		if (AfterimageRenderState.isActive() && componentType == DataComponentTypes.TRIM) {
			return null;
		}
		return stack.get(componentType);
	}
}
