package com.supper.stasis;

import com.supper.stasis.item.ParadoxStateComponent;
import com.supper.stasis.item.StasisChronometer;
import com.supper.stasis.command.StasisCommands;
import com.supper.stasis.network.ParadoxSetCustomLapsePayload;
import com.supper.stasis.network.StasisSyncPayload;
import com.supper.stasis.screen.ParadoxScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Stasis implements ModInitializer {
    public static final String MOD_ID = "stasis";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final RegistryKey<Item> PARADOX_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "paradox"));
    public static final RegistryKey<Item> TEMPORAL_GRIT_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "temporalgrit"));
    public static final RegistryKey<Item> STASIS_CORE_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "stasiscore"));
    public static final RegistryKey<Item> REINFORCED_DIAMOND_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "reinforceddiamond"));
    public static final RegistryKey<Item> REINFORCED_NETHERITE_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "reinforcednetherite"));

    public static final Identifier TIMESTOP_STOP_ID = Identifier.of(MOD_ID, "timestopstop");
    public static final Identifier TIMESTOP_RESUME_ID = Identifier.of(MOD_ID, "timestopresume");
    public static final Identifier TIMESTOP_STASIS_ID = Identifier.of(MOD_ID, "timestopstasis");
    public static final Identifier TIMESTOP_START_ID = Identifier.of(MOD_ID, "timestopstart");
    public static final Identifier TIMESTOP_BREAK_ID = Identifier.of(MOD_ID, "timestopbreak");
    public static final Identifier PARADOX_STATE_ID = Identifier.of(MOD_ID, "paradox_state");
    public static final Identifier PARADOX_SCREEN_HANDLER_ID = Identifier.of(MOD_ID, "paradox_screen");

    public static StasisConfig CONFIG;
    public static Item PARADOX;
    public static Item TEMPORAL_GRIT;
    public static Item STASIS_CORE;
    public static Item REINFORCED_DIAMOND;
    public static Item REINFORCED_NETHERITE;
    public static ComponentType<ParadoxStateComponent> PARADOX_STATE_COMPONENT;
    public static ScreenHandlerType<ParadoxScreenHandler> PARADOX_SCREEN_HANDLER_TYPE;

    public static final SoundEvent TIMESTOP_STOP_SOUND = SoundEvent.of(TIMESTOP_STOP_ID);
    public static final SoundEvent TIMESTOP_RESUME_SOUND = SoundEvent.of(TIMESTOP_RESUME_ID);
    public static final SoundEvent TIMESTOP_STASIS_SOUND = SoundEvent.of(TIMESTOP_STASIS_ID);
    public static final SoundEvent TIMESTOP_START_SOUND = SoundEvent.of(TIMESTOP_START_ID);
    public static final SoundEvent TIMESTOP_BREAK_SOUND = SoundEvent.of(TIMESTOP_BREAK_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Stasis mod...");
        CONFIG = StasisConfig.load();

        PARADOX_STATE_COMPONENT = Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                PARADOX_STATE_ID,
                ComponentType.<ParadoxStateComponent>builder()
                        .codec(ParadoxStateComponent.CODEC)
                        .packetCodec(ParadoxStateComponent.PACKET_CODEC)
                        .cache()
                        .build()
        );

        PARADOX = new StasisChronometer(new Item.Settings().maxCount(1).component(PARADOX_STATE_COMPONENT, ParadoxStateComponent.DEFAULT));
        TEMPORAL_GRIT = new Item(new Item.Settings());
        STASIS_CORE = new Item(new Item.Settings());
        REINFORCED_DIAMOND = new Item(new Item.Settings());
        REINFORCED_NETHERITE = new Item(new Item.Settings());
        PARADOX_SCREEN_HANDLER_TYPE = Registry.register(
                Registries.SCREEN_HANDLER,
                PARADOX_SCREEN_HANDLER_ID,
                new ScreenHandlerType<>(ParadoxScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );

        Registry.register(Registries.ITEM, PARADOX_KEY, PARADOX);
        Registry.register(Registries.ITEM, TEMPORAL_GRIT_KEY, TEMPORAL_GRIT);
        Registry.register(Registries.ITEM, STASIS_CORE_KEY, STASIS_CORE);
        Registry.register(Registries.ITEM, REINFORCED_DIAMOND_KEY, REINFORCED_DIAMOND);
        Registry.register(Registries.ITEM, REINFORCED_NETHERITE_KEY, REINFORCED_NETHERITE);
        Registry.register(Registries.SOUND_EVENT, TIMESTOP_STOP_ID, TIMESTOP_STOP_SOUND);
        Registry.register(Registries.SOUND_EVENT, TIMESTOP_RESUME_ID, TIMESTOP_RESUME_SOUND);
        Registry.register(Registries.SOUND_EVENT, TIMESTOP_STASIS_ID, TIMESTOP_STASIS_SOUND);
        Registry.register(Registries.SOUND_EVENT, TIMESTOP_START_ID, TIMESTOP_START_SOUND);
        Registry.register(Registries.SOUND_EVENT, TIMESTOP_BREAK_ID, TIMESTOP_BREAK_SOUND);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(new ItemStack(PARADOX)));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(new ItemStack(TEMPORAL_GRIT));
            entries.add(new ItemStack(STASIS_CORE));
            entries.add(new ItemStack(REINFORCED_DIAMOND));
            entries.add(new ItemStack(REINFORCED_NETHERITE));
        });

        PayloadTypeRegistry.playS2C().register(StasisSyncPayload.ID, StasisSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ParadoxSetCustomLapsePayload.ID, ParadoxSetCustomLapsePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ParadoxSetCustomLapsePayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (context.player().currentScreenHandler instanceof ParadoxScreenHandler handler
                            && context.player().currentScreenHandler.syncId == payload.syncId()) {
                        handler.setPendingCustomLapseSeconds(payload.totalSeconds());
                    }
                })
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> StasisManager.getInstance().tick(server));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StasisManager.getInstance().syncToClient(handler.player));
        CommandRegistrationCallback.EVENT.register(StasisCommands::register);

        LOGGER.info("Stasis mod initialized!");
    }
}
