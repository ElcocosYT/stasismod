package com.supper.stasis.mixin;

import com.supper.stasis.StasisManager;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Inject(method = "spawnEntity", at = @At("RETURN"))
    private void stasis$freezeFreshlySpawnedEntities(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            StasisManager.getInstance().onEntitySpawned(entity);
        }
    }

    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void stasis$freezeEntityTickLoop(Entity entity, CallbackInfo ci) {
        StasisManager manager = StasisManager.getInstance();
        manager.captureEntityMomentum(entity);
        if (manager.isTransitioning(entity.getEntityWorld())) {
            if (entity instanceof LivingEntity) {
                if (!manager.shouldAdvanceVolatileProgress(entity)) {
                    ci.cancel();
                }
                return;
            }
            manager.prepareTransitionTick(entity);
            manager.captureTransitionTickState(entity);
        }

        if (!manager.shouldFreezeEntity(entity)) {
            return;
        }

        if (!(entity instanceof LivingEntity)) {
            entity.setVelocity(Vec3d.ZERO);
        }
        ci.cancel();
    }

    @Inject(method = "tickEntity", at = @At("RETURN"))
    private void stasis$captureResumeVelocity(Entity entity, CallbackInfo ci) {
        StasisManager manager = StasisManager.getInstance();
        manager.captureResumeVelocity(entity);
        if (manager.isTransitioning(entity.getEntityWorld()) && !(entity instanceof LivingEntity)) {
            manager.applyTransitionTickState(entity);
        }
    }

    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void stasis$freezeScheduledBlockTicks(BlockPos pos, Block block, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (StasisManager.getInstance().shouldFreezeWorld(world)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
    private void stasis$freezeScheduledFluidTicks(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (StasisManager.getInstance().shouldFreezeWorld(world)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void stasis$freezeChunkTicks(WorldChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (StasisManager.getInstance().shouldFreezeWorld(world)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void stasis$freezeTimeProgression(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (StasisManager.getInstance().shouldFreezeWorld(world)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickWeather", at = @At("HEAD"), cancellable = true)
    private void stasis$freezeWeather(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (StasisManager.getInstance().shouldFreezeWorld(world)) {
            ci.cancel();
        }
    }

    @Inject(method = "processSyncedBlockEvents", at = @At("HEAD"), cancellable = true)
    private void stasis$freezeBlockEvents(CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (StasisManager.getInstance().shouldFreezeWorld(world)) {
            ci.cancel();
        }
    }
}
