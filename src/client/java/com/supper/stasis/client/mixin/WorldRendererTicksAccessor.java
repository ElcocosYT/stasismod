package com.supper.stasis.client.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererTicksAccessor {
	@Accessor("ticks")
	int stasis$getTicks();
}
