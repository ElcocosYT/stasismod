package com.supper.stasis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public record StasisConfig(
		int chronometerUses,
		int transitionInTicks,
		int activeTicks,
		int transitionOutTicks
) {
	private static final int DEFAULT_CHRONOMETER_USES = 5;
	private static final int DEFAULT_TRANSITION_IN_TICKS = Math.max(1, (int) Math.round(StasisTimings.STOP_SOUND_SECONDS * 20.0));
	private static final int DEFAULT_ACTIVE_TICKS = 300;
	private static final int DEFAULT_TRANSITION_OUT_TICKS = Math.max(1, (int) Math.round(StasisTimings.RESUME_SOUND_SECONDS * 20.0));

	public static StasisConfig load() {
		Path configPath = FabricLoader.getInstance().getConfigDir().resolve("stasis.properties");
		Properties properties = new Properties();
		if (Files.exists(configPath)) {
			try (InputStream inputStream = Files.newInputStream(configPath)) {
				properties.load(inputStream);
			} catch (IOException exception) {
				Stasis.LOGGER.warn("Failed to read stasis config, using defaults", exception);
			}
		}

		StasisConfig config = new StasisConfig(
				readInt(properties, "chronometerUses", DEFAULT_CHRONOMETER_USES, 1, 128),
				readInt(properties, "transitionInTicks", DEFAULT_TRANSITION_IN_TICKS, 1, 200),
				readInt(properties, "activeTicks", DEFAULT_ACTIVE_TICKS, 20, 72000),
				readInt(properties, "transitionOutTicks", DEFAULT_TRANSITION_OUT_TICKS, 1, 200)
		);
		writeDefaults(configPath, config);
		return config;
	}

	private static int readInt(Properties properties, String key, int defaultValue, int minValue, int maxValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}

		try {
			int parsedValue = Integer.parseInt(value.trim());
			return Math.max(minValue, Math.min(maxValue, parsedValue));
		} catch (NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static void writeDefaults(Path configPath, StasisConfig config) {
		try {
			Files.createDirectories(configPath.getParent());
			Properties output = new Properties();
			output.setProperty("chronometerUses", Integer.toString(config.chronometerUses()));
			output.setProperty("transitionInTicks", Integer.toString(config.transitionInTicks()));
			output.setProperty("activeTicks", Integer.toString(config.activeTicks()));
			output.setProperty("transitionOutTicks", Integer.toString(config.transitionOutTicks()));

			try (OutputStream outputStream = Files.newOutputStream(configPath)) {
				output.store(outputStream, "Stasis configuration");
			}
		} catch (IOException exception) {
			Stasis.LOGGER.warn("Failed to write stasis config defaults", exception);
		}
	}
}
