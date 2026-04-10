package com.supper.stasis.mixin;

import com.supper.stasis.StasisManager;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractPressurePlateBlock.class)
public class AbstractPressurePlateBlockMixin {
	@Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
	private void stasis$queuePressurePlateCollision(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
		if (!world.isClient && world instanceof ServerWorld serverWorld) {
			StasisManager manager = StasisManager.getInstance();
			if (manager.shouldFreezeWorld(world) && manager.isPrivilegedEntity(entity)) {
				manager.queuePressurePlateCollision(serverWorld, pos, entity);
				ci.cancel();
			}
		}
	}
}
