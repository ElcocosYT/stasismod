package com.supper.stasis.client.screen;

import com.supper.stasis.item.ParadoxLapseMode;
import com.supper.stasis.item.ParadoxStateHelper;
import com.supper.stasis.network.ParadoxSetCustomLapsePayload;
import com.supper.stasis.screen.ParadoxScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import com.mojang.blaze3d.systems.RenderSystem;

public class ParadoxHandledScreen extends HandledScreen<ParadoxScreenHandler> {
    private static final int SCREEN_WIDTH = 248;
    private static final int SCREEN_HEIGHT = 248;
    private static final Identifier MAINFRAME_TEXTURE = Identifier.of("stasis", "textures/gui/mainframe.png");
    private static final Identifier APPLY_BUTTON_TEXTURE = Identifier.of("stasis", "textures/gui/applybutton.png");
    private static final Identifier BUTTON_TEXTURE = Identifier.of("stasis", "textures/gui/buttontemplate.png");
    private static final Identifier BLOCKED_BUTTON_TEXTURE = Identifier.of("stasis", "textures/gui/blockedtemplate.png");
    private static final Identifier SET_BUTTON_TEXTURE = Identifier.of("stasis", "textures/gui/setbutton.png");
    private static final Identifier ENDER_PEARL_OVERLAY_TEXTURE = Identifier.of("stasis", "textures/gui/enderpearl.png");

    private static final int APPLY_X = 173;
    private static final int APPLY_Y = 23;
    private static final int APPLY_WIDTH = 63;
    private static final int APPLY_HEIGHT = 59;
    private static final int TIER_TEXT_X = 174;
    private static final int TIER_TEXT_Y = 85;
    private static final int TIER_TEXT_WIDTH = 61;
    private static final int LAPSES_TEXT_X = 174;
    private static final int LAPSES_TEXT_Y = 97;
    private static final int BUTTON_X = 173;
    private static final int BUTTON_WIDTH = 63;
    private static final int BUTTON_HEIGHT = 21;
    private static final int BUTTON_10_Y = 109;
    private static final int BUTTON_20_Y = 132;
    private static final int BUTTON_30_Y = 155;
    private static final int LOCKED_TEXT_X = 174;
    private static final int LOCKED_TEXT_Y = 177;
    private static final int LOCKED_TEXT_WIDTH = 61;
    private static final int INPUT_X = 173;
    private static final int INPUT_Y = 190;
    private static final int INPUT_WIDTH = 63;
    private static final int INPUT_HEIGHT = 21;
    private static final int SET_X = 173;
    private static final int SET_Y = 211;
    private static final int SET_WIDTH = 63;
    private static final int SET_HEIGHT = 12;
    private static final int ENDER_PEARL_X = 38;
    private static final int ENDER_PEARL_Y = 27;
    private static final int ENDER_PEARL_WIDTH = 94;
    private static final int ENDER_PEARL_HEIGHT = 94;

    private ButtonWidget preset10Button;
    private ButtonWidget preset20Button;
    private ButtonWidget preset30Button;
    private ButtonWidget setCustomButton;
    private ButtonWidget applyButton;
    private TextFieldWidget customInput;

    public ParadoxHandledScreen(ParadoxScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = SCREEN_WIDTH;
        this.backgroundHeight = SCREEN_HEIGHT;
        this.playerInventoryTitleX = 6;
        this.playerInventoryTitleY = 135;
        this.titleX = 18;
        this.titleY = 8;
    }

    @Override
    protected void init() {
        super.init();
        this.preset10Button = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, ParadoxScreenHandler.BUTTON_SELECT_10);
            }
        }).dimensions(this.x + BUTTON_X, this.y + BUTTON_10_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.preset20Button = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, ParadoxScreenHandler.BUTTON_SELECT_20);
            }
        }).dimensions(this.x + BUTTON_X, this.y + BUTTON_20_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.preset30Button = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, ParadoxScreenHandler.BUTTON_SELECT_30);
            }
        }).dimensions(this.x + BUTTON_X, this.y + BUTTON_30_Y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.customInput = addDrawableChild(new CenteredInputWidget(this.textRenderer, this.x + INPUT_X, this.y + INPUT_Y, INPUT_WIDTH, INPUT_HEIGHT, Text.literal("Custom")));
        this.customInput.setMaxLength(5);
        this.customInput.setText(ParadoxStateHelper.formatSeconds(this.handler.getCustomSeconds()));
        this.customInput.setChangedListener(this::sanitizeCustomInput);

        this.setCustomButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> submitCustomTime()).dimensions(this.x + SET_X, this.y + SET_Y, SET_WIDTH, SET_HEIGHT).build());

        this.applyButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            playUiSound(SoundEvents.BLOCK_SMITHING_TABLE_USE, 1.0f, 1.0f);
            if (this.client != null && this.client.interactionManager != null) {
                this.client.interactionManager.clickButton(this.handler.syncId, ParadoxScreenHandler.BUTTON_APPLY);
            }
        }).dimensions(this.x + APPLY_X, this.y + APPLY_Y, APPLY_WIDTH, APPLY_HEIGHT).build());

        this.preset10Button.setAlpha(0.0f);
        this.preset20Button.setAlpha(0.0f);
        this.preset30Button.setAlpha(0.0f);
        this.setCustomButton.setAlpha(0.0f);
        this.applyButton.setAlpha(0.0f);

        refreshWidgets();
    }

    @Override
    public void handledScreenTick() {
        super.handledScreenTick();
        if (!this.customInput.isFocused()) {
            this.customInput.setText(ParadoxStateHelper.formatSeconds(this.handler.getCustomSeconds()));
        }
        refreshWidgets();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawDecorativeOverlays(context);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;
        context.drawTexture(MAINFRAME_TEXTURE, left, top, 0.0f, 0.0f, this.backgroundWidth, this.backgroundHeight, this.backgroundWidth, this.backgroundHeight);
        drawTexturedButton(context, APPLY_BUTTON_TEXTURE, this.applyButton, APPLY_WIDTH, APPLY_HEIGHT, false);
        drawTexturedButton(context, BUTTON_TEXTURE, this.preset10Button, BUTTON_WIDTH, BUTTON_HEIGHT, false);
        drawTexturedButton(context, this.preset20Button.active ? BUTTON_TEXTURE : BLOCKED_BUTTON_TEXTURE, this.preset20Button, BUTTON_WIDTH, BUTTON_HEIGHT, false);
        drawTexturedButton(context, this.preset30Button.active ? BUTTON_TEXTURE : BLOCKED_BUTTON_TEXTURE, this.preset30Button, BUTTON_WIDTH, BUTTON_HEIGHT, false);
        context.drawTexture(BLOCKED_BUTTON_TEXTURE, left + INPUT_X, top + INPUT_Y, 0.0f, 0.0f, INPUT_WIDTH, INPUT_HEIGHT, INPUT_WIDTH, INPUT_HEIGHT);
        drawTexturedButton(context, SET_BUTTON_TEXTURE, this.setCustomButton, SET_WIDTH, SET_HEIGHT, !this.setCustomButton.active);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        boolean customUnlocked = this.handler.getCurrentTier() >= ParadoxStateHelper.TIER_THREE;
        drawCenteredText(context, Text.literal("Tier " + toRoman(this.handler.getCurrentTier())), TIER_TEXT_X + TIER_TEXT_WIDTH / 2, TIER_TEXT_Y, 0xD7C083);
        context.drawText(this.textRenderer, Text.literal("Lapses"), LAPSES_TEXT_X, LAPSES_TEXT_Y, 0x70888D, false);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0xFFFFFF, false);
        if (this.handler.getCurrentTier() < ParadoxStateHelper.TIER_THREE) {
            drawCenteredText(context, Text.literal("Locked").formatted(Formatting.DARK_GRAY), LOCKED_TEXT_X + LOCKED_TEXT_WIDTH / 2, LOCKED_TEXT_Y, 0x70888D);
        }
        drawButtonLabel(context, this.preset10Button, Text.literal("10s"), this.handler.getSelectedMode() == ParadoxLapseMode.PRESET_10);
        drawButtonLabel(context, this.preset20Button, Text.literal("20s"), this.handler.getSelectedMode() == ParadoxLapseMode.PRESET_20);
        drawButtonLabel(context, this.preset30Button, Text.literal("30s"), this.handler.getSelectedMode() == ParadoxLapseMode.PRESET_30);
        drawCenteredText(context, Text.literal(this.customInput.getText().isEmpty() ? "00:00" : this.customInput.getText()), INPUT_X + INPUT_WIDTH / 2, INPUT_Y + 7, customUnlocked ? 0xD3E6E8 : 0x70888D);
        drawCenteredText(context, Text.literal("Set"), SET_X + SET_WIDTH / 2, SET_Y + 2, this.setCustomButton.active ? 0xD3E6E8 : 0x70888D);
    }

    private void refreshWidgets() {
        ParadoxLapseMode selectedMode = this.handler.getSelectedMode();
        int tier = this.handler.getCurrentTier();
        this.preset10Button.active = true;
        this.preset20Button.active = tier >= ParadoxStateHelper.TIER_TWO;
        this.preset30Button.active = tier >= ParadoxStateHelper.TIER_THREE;
        this.customInput.setEditable(tier >= ParadoxStateHelper.TIER_THREE);
        this.customInput.setEditableColor(0x000000);
        this.customInput.setUneditableColor(0x000000);
        this.setCustomButton.active = tier >= ParadoxStateHelper.TIER_THREE;

        this.preset10Button.setMessage(Text.empty());
        this.preset20Button.setMessage(Text.empty());
        this.preset30Button.setMessage(Text.empty());
        this.applyButton.setMessage(Text.literal(""));
    }

    private void sanitizeCustomInput(String value) {
        String sanitized = value.replaceAll("[^0-9:]", "");
        if (sanitized.length() > 5) {
            sanitized = sanitized.substring(0, 5);
        }
        if (!sanitized.equals(value)) {
            this.customInput.setText(sanitized);
        }
    }

    private void submitCustomTime() {
        Integer parsedSeconds = parseCustomSeconds(this.customInput.getText());
        if (parsedSeconds == null) {
            return;
        }
        this.customInput.setText(ParadoxStateHelper.formatSeconds(parsedSeconds));
        ClientPlayNetworking.send(new ParadoxSetCustomLapsePayload(this.handler.syncId, parsedSeconds));
    }

    private Integer parseCustomSeconds(String text) {
        String[] parts = text.split(":", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        try {
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            if (minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
                return null;
            }
            int totalSeconds = minutes * 60 + seconds;
            if (totalSeconds < ParadoxStateHelper.MIN_CUSTOM_SECONDS || totalSeconds > ParadoxStateHelper.MAX_CUSTOM_SECONDS) {
                return null;
            }
            return totalSeconds;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void drawCenteredText(DrawContext context, Text text, int centerX, int y, int color) {
        int width = this.textRenderer.getWidth(text);
        context.drawText(this.textRenderer, text, centerX - width / 2, y, color, false);
    }

    private void drawButtonLabel(DrawContext context, ButtonWidget button, Text label, boolean selected) {
        int color;
        if (!button.active) {
            color = 0x70888D;
        } else if (selected) {
            color = 0xF0B220;
        } else {
            color = 0xD3E6E8;
        }
        drawCenteredText(context, label, button.getX() - this.x + button.getWidth() / 2, button.getY() - this.y + 6, color);
    }

    private void drawTexturedButton(DrawContext context, Identifier texture, ButtonWidget button, int width, int height, boolean disabled) {
        context.drawTexture(texture, button.getX(), button.getY(), 0.0f, 0.0f, width, height, width, height);
        if (button.isHovered() && button.active) {
            context.fill(button.getX(), button.getY(), button.getX() + width, button.getY() + height, 0x22FFFFFF);
        }
        if (disabled) {
            context.fill(button.getX(), button.getY(), button.getX() + width, button.getY() + height, 0x88000000);
        }
    }



    private void drawDecorativeOverlays(DrawContext context) {
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 500);
        context.drawTexture(
                ENDER_PEARL_OVERLAY_TEXTURE,
                this.x + ENDER_PEARL_X,
                this.y + ENDER_PEARL_Y,
                0.0f,
                0.0f,
                ENDER_PEARL_WIDTH,
                ENDER_PEARL_HEIGHT,
                ENDER_PEARL_WIDTH,
                ENDER_PEARL_HEIGHT
        );
        context.getMatrices().pop();
    }

    private void playUiSound(SoundEvent sound, float volume, float pitch) {
        MinecraftClient client = this.client;
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(sound, volume, pitch));
        }
    }

    private String toRoman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    private static final class CenteredInputWidget extends TextFieldWidget {
        private CenteredInputWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
            super(textRenderer, x, y, width, height, text);
            setDrawsBackground(false);
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        }
    }
}
