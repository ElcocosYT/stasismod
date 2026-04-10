package com.supper.stasis.client.render;

import com.supper.stasis.client.StasisClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class StasisHudOverlay {
    private StasisHudOverlay() {
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden || !StasisClientState.isActive()) {
            return;
        }
        int activeTicksRemaining = StasisClientState.getActiveTicksRemaining();
        if (activeTicksRemaining <= 0) {
            return;
        }
        int secondsRemaining = Math.max(0, (int) Math.ceil(activeTicksRemaining / 20.0));
        int minutes = secondsRemaining / 60;
        int seconds = secondsRemaining % 60;
        Text text = Text.literal(String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds));
        int color = withOpaqueAlpha(activeTicksRemaining <= StasisClientState.getWarningTicks() ? 0xFF5555 : 0xFFFFFF);
        int textWidth = client.textRenderer.getWidth(text);
        float scale = 1.8f;
        int x = Math.round((context.getScaledWindowWidth() / 2.0f) / scale - textWidth / 2.0f);
        int y = Math.round(10.0f / scale);
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        context.drawText(client.textRenderer, text, x, y, color, true);
        context.getMatrices().popMatrix();
    }

    private static int withOpaqueAlpha(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }
}
