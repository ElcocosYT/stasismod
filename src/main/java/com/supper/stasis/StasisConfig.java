package com.supper.stasis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public record StasisConfig(
		boolean trailsActive,
		boolean trailsRenderCapes,
		int trailsAmountLimit,
		String trailsGenerationType,
		double trailsContinuousTimings,
		boolean trailsMidSecondsGeneration
) {
	private static final boolean DEFAULT_TRAILS_ACTIVE = true;
	private static final boolean DEFAULT_TRAILS_RENDER_CAPES = true;
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
				readBoolean(properties, "TrailsActive", DEFAULT_TRAILS_ACTIVE),
				readBoolean(properties, "TrailsRenderCapes", DEFAULT_TRAILS_RENDER_CAPES),
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
			try (var writer = Files.newBufferedWriter(
					configPath,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			)) {
				writer.write("# Stasis configuration");
				writer.newLine();
				writer.write("#");
				writer.newLine();
				writer.write("# TrailsActive: when false, player trails are not generated or rendered.");
				writer.newLine();
				writer.write("TrailsActive=" + config.trailsActive());
				writer.newLine();
				writer.newLine();

				writer.write("# TrailsRenderCapes: enables or disables cape rendering on player trail ghosts.");
				writer.newLine();
				writer.write("TrailsRenderCapes=" + config.trailsRenderCapes());
				writer.newLine();
				writer.newLine();

				writer.write("# TrailsAmountLimit: maximum number of trails kept in memory.");
				writer.newLine();
				writer.write("# When the limit is reached, the oldest trail is removed first.");
				writer.newLine();
				writer.write("TrailsAmountLimit=" + config.trailsAmountLimit());
				writer.newLine();
				writer.newLine();

				writer.write("# TrailsGenerationType: 'C' = continuous, 'S' = seconds.");
				writer.newLine();
				writer.write("# C: generates trails continuously using TrailsContinuousTimings as the interval.");
				writer.newLine();
				writer.write("# S: generates trails once per in-game second (optionally also at half-seconds).");
				writer.newLine();
				writer.write("TrailsGenerationType=" + config.trailsGenerationType());
				writer.newLine();
				writer.newLine();

				writer.write("# TrailsContinuousTimings: time between generated trails (in seconds).");
				writer.newLine();
				writer.write("# Only used when TrailsGenerationType=C.");
				writer.newLine();
				writer.write("TrailsContinuousTimings=" + String.format("%.2f", config.trailsContinuousTimings()));
				writer.newLine();
				writer.newLine();

				writer.write("# TrailsMidSecondsGeneration: if true and TrailsGenerationType=S,");
				writer.newLine();
				writer.write("# also generates a trail at half-second intervals (2 trails per second).");
				writer.newLine();
				writer.write("TrailsMidSecondsGeneration=" + config.trailsMidSecondsGeneration());
				writer.newLine();
			}
		} catch (IOException exception) {
			Stasis.LOGGER.warn("Failed to write stasis config defaults", exception);
		}
	}
}
