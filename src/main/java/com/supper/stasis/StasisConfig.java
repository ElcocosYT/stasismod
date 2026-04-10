package com.supper.stasis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public record StasisConfig(
		int trailsAmountLimit,
		String trailsGenerationType,
		double trailsContinuousTimings,
		boolean trailsMidSecondsGeneration
) {
	private static final int DEFAULT_TRAILS_AMOUNT_LIMIT = 100;
	private static final String DEFAULT_TRAILS_GENERATION_TYPE = "C";
	private static final double DEFAULT_TRAILS_CONTINUOUS_TIMINGS = 0.05;
	private static final boolean DEFAULT_TRAILS_MID_SECONDS_GENERATION = false;

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
				readInt(properties, "TrailsAmountLimit", DEFAULT_TRAILS_AMOUNT_LIMIT, 1, 1000),
				readString(properties, "TrailsGenerationType", DEFAULT_TRAILS_GENERATION_TYPE),
				readDouble(properties, "TrailsContinuousTimings", DEFAULT_TRAILS_CONTINUOUS_TIMINGS, 0.01, 10.0),
				readBoolean(properties, "TrailsMidSecondsGeneration", DEFAULT_TRAILS_MID_SECONDS_GENERATION)
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

	private static String readString(Properties properties, String key, String defaultValue) {
		String value = properties.getProperty(key);
		return value != null ? value.trim() : defaultValue;
	}

	private static double readDouble(Properties properties, String key, double defaultValue, double minValue, double maxValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}

		try {
			double parsedValue = Double.parseDouble(value.trim());
			return Math.max(minValue, Math.min(maxValue, parsedValue));
		} catch (NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static boolean readBoolean(Properties properties, String key, boolean defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value.trim());
	}

	private static void writeDefaults(Path configPath, StasisConfig config) {
		try {
			Files.createDirectories(configPath.getParent());
			Properties output = new Properties();
			output.setProperty("TrailsAmountLimit", Integer.toString(config.trailsAmountLimit()));
			output.setProperty("TrailsGenerationType", config.trailsGenerationType());
			output.setProperty("TrailsContinuousTimings", String.format("%.2f", config.trailsContinuousTimings()));
			output.setProperty("TrailsMidSecondsGeneration", Boolean.toString(config.trailsMidSecondsGeneration()));

			try (OutputStream outputStream = Files.newOutputStream(configPath)) {
				output.store(outputStream, "Stasis configuration");
			}
		} catch (IOException exception) {
			Stasis.LOGGER.warn("Failed to write stasis config defaults", exception);
		}
	}
}
