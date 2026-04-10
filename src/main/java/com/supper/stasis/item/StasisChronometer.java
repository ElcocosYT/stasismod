package com.supper.stasis.item;

import com.supper.stasis.Stasis;
import com.supper.stasis.StasisManager;
import com.supper.stasis.screen.ParadoxScreenHandler;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StasisChronometer extends Item {
    public StasisChronometer(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        ParadoxStateHelper.getState(stack);
        if (user.isSneaking()) {
            if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
                openScreen(serverPlayer, hand);
            }
            return TypedActionResult.success(stack, world.isClient());
        }
        return tryActivate(world, user, hand, stack)
                ? TypedActionResult.success(stack, world.isClient())
                : TypedActionResult.fail(stack);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) {
            return ActionResult.PASS;
        }
        ItemStack stack = context.getStack();
        ParadoxStateHelper.getState(stack);
        if (player.isSneaking()) {
            if (!context.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                openScreen(serverPlayer, context.getHand());
            }
            return ActionResult.SUCCESS;
        }
        return tryActivate(context.getWorld(), player, context.getHand(), stack) ? ActionResult.SUCCESS : ActionResult.FAIL;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        ParadoxStateComponent state = ParadoxStateHelper.getState(stack);
        return state.gritCount() < ParadoxStateHelper.getMaxGrits(state);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        ParadoxStateComponent state = ParadoxStateHelper.getState(stack);
        return Math.round(13.0f * state.gritCount() / ParadoxStateHelper.getMaxGrits(state));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        int gritCount = ParadoxStateHelper.getState(stack).gritCount();
        return gritCount <= 1 ? 0xFF5555 : 0x55FF55;
    }

    @Override
    public Text getName(ItemStack stack) {
        ParadoxStateComponent state = ParadoxStateHelper.getState(stack);
        return Text.translatable(this.getTranslationKey(stack)).formatted(getNameFormatting(state));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        ParadoxStateComponent state = ParadoxStateHelper.getState(stack);
        tooltip.add(Text.literal("Tier " + roman(state.tier())).formatted(getTierFormatting(state)));
        tooltip.add(Text.literal("Lapse: " + ParadoxStateHelper.getSelectedLapseSeconds(state) + "s").formatted(Formatting.GREEN));
        tooltip.add(Text.literal("The power to stop time entirely, right in your hands.").formatted(Formatting.GRAY, Formatting.ITALIC));
    }

    private boolean tryActivate(World world, PlayerEntity user, Hand hand, ItemStack stack) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return true;
        }
        if (!ParadoxStateHelper.isUsable(stack)) {
            return false;
        }
        ParadoxStateComponent state = ParadoxStateHelper.getState(stack);
        StasisManager manager = StasisManager.getInstance();
        if (!manager.activate(serverWorld, user.getUuid(), ParadoxStateHelper.getSelectedLapseSeconds(state) * 20, ParadoxStateHelper.getWarningTicks(state))) {
            return false;
        }
        if (!ParadoxStateHelper.consumeUse(stack)) {
            return false;
        }
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                Stasis.TIMESTOP_START_SOUND, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                Stasis.TIMESTOP_STOP_SOUND, SoundCategory.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    private void openScreen(ServerPlayerEntity player, Hand hand) {
        if (StasisManager.getInstance().getPhase().isRunning()) {
            return;
        }
        ItemStack stack = player.getStackInHand(hand);
        ParadoxStateComponent state = ParadoxStateHelper.getState(stack);
        int slotIndex = hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : PlayerInventory.OFF_HAND_SLOT;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, playerEntity) -> new ParadoxScreenHandler(syncId, inventory, hand, slotIndex, state.itemUuid().orElse(null)),
                Text.translatable("screen.stasis.paradox")
        ));
    }

    private String roman(int tier) {
        return switch (tier) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    private Formatting getTierFormatting(ParadoxStateComponent state) {
        return switch (state.tier()) {
            case ParadoxStateHelper.TIER_TWO -> Formatting.DARK_AQUA;
            case ParadoxStateHelper.TIER_THREE -> Formatting.DARK_GRAY;
            default -> Formatting.GOLD;
        };
    }

    private Formatting getNameFormatting(ParadoxStateComponent state) {
        return switch (state.tier()) {
            case ParadoxStateHelper.TIER_TWO -> Formatting.AQUA;
            case ParadoxStateHelper.TIER_THREE -> Formatting.LIGHT_PURPLE;
            default -> Formatting.YELLOW;
        };
    }
}
