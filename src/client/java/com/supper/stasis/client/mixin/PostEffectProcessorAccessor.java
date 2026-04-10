package com.supper.stasis.client.mixin;

import java.util.List;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebufferFactory;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PostEffectProcessor.class)
public interface PostEffectProcessorAccessor {
	@Accessor("passes")
	List<PostEffectPass> stasis$getPasses();

	@Invoker("createFramebuffer")
	Framebuffer stasis$createFramebuffer(Identifier id, SimpleFramebufferFactory factory);
}
