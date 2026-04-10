package com.supper.stasis.client.mixin;

import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.RenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderDispatcher.class)
public interface RenderDispatcherAccessor {
	@Accessor("vertexConsumers")
	VertexConsumerProvider.Immediate stasis$getVertexConsumers();

	@Accessor("outlineVertexConsumers")
	OutlineVertexConsumerProvider stasis$getOutlineVertexConsumers();

	@Accessor("crumblingOverlayVertexConsumers")
	VertexConsumerProvider.Immediate stasis$getCrumblingOverlayVertexConsumers();
}
