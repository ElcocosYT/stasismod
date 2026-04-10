package com.supper.stasis.client.mixin;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public class CameraMixin {
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float stasis$useTransitionDeltaForRestrictedLocalPlayer(
            float tickDelta,
            World area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float originalTickDelta
    ) {
        if (!StasisClientState.isTransitioningRestrictedLocalPlayer(focusedEntity)) {
            return tickDelta;
        }

        return StasisClientState.getEntityRenderTickDelta(focusedEntity, tickDelta);
    }
}
