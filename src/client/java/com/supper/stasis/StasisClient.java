package com.supper.stasis.client;

import com.supper.stasis.Stasis;
import com.supper.stasis.client.render.PlayerTrailRenderer;
import com.supper.stasis.client.render.StasisHudOverlay;
import com.supper.stasis.client.render.StasisShaderManager;
import com.supper.stasis.client.screen.ParadoxHandledScreen;
import com.supper.stasis.client.sound.StasisLoopSoundController;
import com.supper.stasis.network.StasisSyncPayload;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class StasisClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StasisSyncPayload.ID, (payload, player, responseSender) -> {
            StasisClientState.apply(payload);
            if (!payload.phase().isRunning()) {
                StasisShaderManager.cleanup();
                StasisLoopSoundController.stop(MinecraftClient.getInstance());
            }
        });

        HandledScreens.register(Stasis.PARADOX_SCREEN_HANDLER_TYPE, ParadoxHandledScreen::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlayerTrailRenderer.onClientTick(client);
            StasisLoopSoundController.tick(client);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PlayerTrailRenderer::render);
        HudRenderCallback.EVENT.register((drawContext, renderTickCounter) -> StasisHudOverlay.render(drawContext));
    }
}
