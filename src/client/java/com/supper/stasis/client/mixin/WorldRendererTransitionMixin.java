package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererTransitionMixin {

    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(
        method = "renderEntity(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void stasis$renderLivingEntitiesWithTransitionDelta(
            Entity entity,
            double cameraX, double cameraY, double cameraZ,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            CallbackInfo ci
    ) {
        if (!(entity instanceof LivingEntity)) {
            return;
        }

        if (!(entity.getWorld() instanceof ClientWorld clientWorld)) {
            return;
        }

        if (!StasisClientState.affectsWorld(clientWorld) || StasisClientState.isPrivilegedEntity(entity)) {
            return;
        }

        float spatialTickDelta = StasisClientState.getEntityRenderTickDelta(entity, tickDelta);
        double x = MathHelper.lerp(spatialTickDelta, entity.prevX, entity.getX());
        double y = MathHelper.lerp(spatialTickDelta, entity.prevY, entity.getY());
        double z = MathHelper.lerp(spatialTickDelta, entity.prevZ, entity.getZ());
        float yaw = entity.prevYaw + MathHelper.wrapDegrees(entity.getYaw() - entity.prevYaw) * spatialTickDelta;
        int light = this.entityRenderDispatcher.getLight(entity, spatialTickDelta);

        this.entityRenderDispatcher.render(
                entity,
                x - cameraX,
                y - cameraY,
                z - cameraZ,
                yaw,
                spatialTickDelta,
                matrices,
                vertexConsumers,
                light
        );
        ci.cancel();
    }
}
