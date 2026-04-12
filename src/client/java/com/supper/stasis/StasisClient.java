package com.supper.stasis.client;

import com.supper.stasis.Stasis;
import com.supper.stasis.client.render.PlayerTrailRenderer;
import com.supper.stasis.client.render.StasisHudOverlay;
import com.supper.stasis.client.render.StasisShaderManager;
import com.supper.stasis.client.screen.ParadoxHandledScreen;
import com.supper.stasis.client.sound.StasisLoopSoundController;
import com.supper.stasis.network.StasisSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class StasisClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StasisSyncPayload.ID, (payload, context) -> {
            StasisClientState.apply(payload);
            if (!payload.phase().isRunning()) {
                StasisShaderManager.cleanup();
                StasisLoopSoundController.stop(context.client());
            }
        });

        HandledScreens.register(Stasis.PARADOX_SCREEN_HANDLER_TYPE, ParadoxHandledScreen::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlayerTrailRenderer.onClientTick(client);
            StasisLoopSoundController.tick(client);
        });
        // After translucent terrain so depth matches Sodium/Iris; before weather (END_MAIN ordering).
        WorldRenderEvents.END_MAIN.register(PlayerTrailRenderer::render);
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> StasisHudOverlay.render(drawContext));
    }
}
