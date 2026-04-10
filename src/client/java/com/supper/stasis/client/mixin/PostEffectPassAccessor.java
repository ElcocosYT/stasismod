package com.supper.stasis.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import java.util.Map;
import net.minecraft.client.gl.PostEffectPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostEffectPass.class)
public interface PostEffectPassAccessor {
	@Accessor("uniformBuffers")
	Map<String, GpuBuffer> stasis$getUniformBuffers();
}
