package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldTransitionMixin {

    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void stasis$freezeClientEntityTick(Entity entity, CallbackInfo ci) {
        ClientWorld world = (ClientWorld) (Object) this;
        if (!StasisClientState.affectsWorld(world)) {
            return;
        }

        StasisClientState.captureEntityMomentum(entity);
        if (entity instanceof LivingEntity livingEntity && StasisClientState.shouldFreezeVolatileProgress(entity)) {
            boolean shouldAdvance = StasisClientState.shouldAdvanceVolatileProgress(entity);
            StasisClientState.onLivingTransitionTickDecision(livingEntity, shouldAdvance);
            if (!shouldAdvance) {
                if (entity instanceof ClientPlayerEntity clientPlayer && StasisClientState.isRestrictedLocalPlayer(entity)) {
                    StasisClientState.stabilizeRestrictedLocalPlayer(clientPlayer);
                }
                ci.cancel();
                return;
            }
            if (StasisClientState.isRunning()) {
                return;
            }
        }

        if (StasisClientState.isTransitioning()) {
            StasisClientState.prepareTransitionTick(entity);
            StasisClientState.captureTransitionTickState(entity);
        }


        if (!StasisClientState.shouldFreezeEntity(entity)) {
            return;
        }

        // Keep previous/current render state identical whenever we cancel a tick to avoid
        // interpolation pulling the entity toward stale positions (body and shadow jitter).
        stasis$syncPreviousRenderState(entity);

        if (!(entity instanceof LivingEntity)) {
            entity.setVelocity(0.0, 0.0, 0.0);
        }
        ci.cancel();

    }

    @Inject(method = "tickEntity", at = @At("RETURN"))
    private void stasis$applyClientTransitionTickState(Entity entity, CallbackInfo ci) {
        StasisClientState.captureResumeVelocity(entity);
        if (StasisClientState.isTransitioning() && entity instanceof LivingEntity livingEntity) {
            StasisClientState.onLivingTransitionTickApplied(livingEntity);
            return;
        }
        if (StasisClientState.isTransitioning() && !(entity instanceof LivingEntity)) {
            StasisClientState.applyTransitionTickState(entity);
        }
    }

    private static void stasis$syncPreviousRenderState(Entity entity) {
        entity.prevX = entity.getX();
        entity.prevY = entity.getY();
        entity.prevZ = entity.getZ();
        entity.lastRenderX = entity.getX();
        entity.lastRenderY = entity.getY();
        entity.lastRenderZ = entity.getZ();
        entity.prevYaw = entity.getYaw();
        entity.prevPitch = entity.getPitch();
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.prevBodyYaw = livingEntity.getBodyYaw();
            livingEntity.prevHeadYaw = livingEntity.getHeadYaw();
        }
    }

}
