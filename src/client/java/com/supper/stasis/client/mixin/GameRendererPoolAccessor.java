package com.supper.stasis.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Pool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererPoolAccessor {
	@Accessor("pool")
	Pool stasis$getPool();
}
