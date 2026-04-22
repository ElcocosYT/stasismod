package com.supper.stasis;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
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
	private static final String CONFIG_FILE_NAME = "stasis-paradox.json";
	private static final String LEGACY_CONFIG_FILE_NAME = "stasis.properties";
	private static final boolean DEFAULT_TRAILS_ACTIVE = true;
	private static final boolean DEFAULT_TRAILS_RENDER_CAPES = true;
	private static final int DEFAULT_TRAILS_AMOUNT_LIMIT = 70;
	private static final String DEFAULT_TRAILS_GENERATION_TYPE = "C";
	private static final double DEFAULT_TRAILS_CONTINUOUS_TIMINGS = 0.10;
	private static final boolean DEFAULT_TRAILS_MID_SECONDS_GENERATION = false;

	public StasisConfig {
		trailsAmountLimit = clampInt(trailsAmountLimit, 1, 1000);
		trailsGenerationType = normalizeGenerationType(trailsGenerationType);
		trailsContinuousTimings = clampDouble(trailsContinuousTimings, 0.01, 60.0);
	}

	public static StasisConfig load() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		Path configPath = configDir.resolve(CONFIG_FILE_NAME);
		Path legacyConfigPath = configDir.resolve(LEGACY_CONFIG_FILE_NAME);

		StasisConfig config = readJsonConfig(configPath);
		if (config == null) {
			config = readLegacyPropertiesConfig(legacyConfigPath);
			if (config != null) {
				Stasis.LOGGER.info("Migrated legacy {} to {}", LEGACY_CONFIG_FILE_NAME, CONFIG_FILE_NAME);
			} else {
				config = defaults();
			}
		}

		writeJsonConfig(configPath, config);
		return config;
	}

	public int maxSnapshots() {
		return this.trailsAmountLimit;
	}

	public int captureIntervalTicks() {
		if ("S".equals(this.trailsGenerationType)) {
			return this.trailsMidSecondsGeneration ? 10 : 20;
		}
		return Math.max(1, (int)Math.round(this.trailsContinuousTimings * 20.0));
	}

	public boolean shouldInterpolateTrailMovement() {
		return "C".equals(this.trailsGenerationType) && this.captureIntervalTicks() <= 2;
	}

	private static StasisConfig defaults() {
		return new StasisConfig(
				DEFAULT_TRAILS_ACTIVE,
				DEFAULT_TRAILS_RENDER_CAPES,
				DEFAULT_TRAILS_AMOUNT_LIMIT,
				DEFAULT_TRAILS_GENERATION_TYPE,
				DEFAULT_TRAILS_CONTINUOUS_TIMINGS,
				DEFAULT_TRAILS_MID_SECONDS_GENERATION
		);
	}

	private static StasisConfig readJsonConfig(Path configPath) {
		if (!Files.exists(configPath)) {
			return null;
		}

		try {
			JsonElement parsed = JsonParser.parseString(stripJsonComments(Files.readString(configPath)));
			if (!parsed.isJsonObject()) {
				throw new JsonParseException("Expected a JSON object");
			}

			JsonObject root = parsed.getAsJsonObject();
			return new StasisConfig(
					readBoolean(root, "TrailsActive", DEFAULT_TRAILS_ACTIVE),
					readBoolean(root, "TrailsRenderCapes", DEFAULT_TRAILS_RENDER_CAPES),
					readInt(root, "TrailsAmountLimit", DEFAULT_TRAILS_AMOUNT_LIMIT),
					readString(root, "TrailsGenerationType", DEFAULT_TRAILS_GENERATION_TYPE),
					readDouble(root, "TrailsContinuousTimings", DEFAULT_TRAILS_CONTINUOUS_TIMINGS),
					readBoolean(root, "TrailsMidSecondsGeneration", DEFAULT_TRAILS_MID_SECONDS_GENERATION)
			);
		} catch (IOException | JsonParseException exception) {
			Stasis.LOGGER.warn("Failed to read {}, using fallback config", CONFIG_FILE_NAME, exception);
			return null;
		}
	}

	private static StasisConfig readLegacyPropertiesConfig(Path legacyConfigPath) {
		if (!Files.exists(legacyConfigPath)) {
			return null;
		}

		Properties properties = new Properties();
		try (InputStream inputStream = Files.newInputStream(legacyConfigPath)) {
			properties.load(inputStream);
			return new StasisConfig(
					readBoolean(properties, "TrailsActive", DEFAULT_TRAILS_ACTIVE),
					readBoolean(properties, "TrailsRenderCapes", DEFAULT_TRAILS_RENDER_CAPES),
					readInt(properties, "TrailsAmountLimit", DEFAULT_TRAILS_AMOUNT_LIMIT),
					readString(properties, "TrailsGenerationType", DEFAULT_TRAILS_GENERATION_TYPE),
					readDouble(properties, "TrailsContinuousTimings", DEFAULT_TRAILS_CONTINUOUS_TIMINGS),
					readBoolean(properties, "TrailsMidSecondsGeneration", DEFAULT_TRAILS_MID_SECONDS_GENERATION)
			);
		} catch (IOException exception) {
			Stasis.LOGGER.warn("Failed to read legacy {}, using fallback config", LEGACY_CONFIG_FILE_NAME, exception);
			return null;
		}
	}

	private static boolean readBoolean(JsonObject root, String key, boolean defaultValue) {
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull()) {
			return defaultValue;
		}

		try {
			return element.getAsBoolean();
		} catch (UnsupportedOperationException | ClassCastException exception) {
			return defaultValue;
		}
	}

	private static int readInt(JsonObject root, String key, int defaultValue) {
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull()) {
			return defaultValue;
		}

		try {
			return element.getAsInt();
		} catch (UnsupportedOperationException | ClassCastException | NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static String readString(JsonObject root, String key, String defaultValue) {
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull()) {
			return defaultValue;
		}

		try {
			return element.getAsString();
		} catch (UnsupportedOperationException | ClassCastException exception) {
			return defaultValue;
		}
	}

	private static double readDouble(JsonObject root, String key, double defaultValue) {
		JsonElement element = root.get(key);
		if (element == null || element.isJsonNull()) {
			return defaultValue;
		}

		try {
			return element.getAsDouble();
		} catch (UnsupportedOperationException | ClassCastException | NumberFormatException exception) {
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

	private static int readInt(Properties properties, String key, int defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static String readString(Properties properties, String key, String defaultValue) {
		String value = properties.getProperty(key);
		return value != null ? value.trim() : defaultValue;
	}

	private static double readDouble(Properties properties, String key, double defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}

		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException exception) {
			return defaultValue;
		}
	}

	private static void writeJsonConfig(Path configPath, StasisConfig config) {
		try {
			Files.createDirectories(configPath.getParent());

			JsonObject root = new JsonObject();
			root.addProperty("TrailsActive", config.trailsActive());
			root.addProperty("TrailsRenderCapes", config.trailsRenderCapes());
			root.addProperty("TrailsAmountLimit", config.trailsAmountLimit());
			root.addProperty("TrailsGenerationType", config.trailsGenerationType());
			root.addProperty("TrailsContinuousTimings", config.trailsContinuousTimings());
			root.addProperty("TrailsMidSecondsGeneration", config.trailsMidSecondsGeneration());

			Files.writeString(
					configPath,
					buildJsonConfigWithComments(root),
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE
			);
		} catch (IOException exception) {
			Stasis.LOGGER.warn("Failed to write {}", CONFIG_FILE_NAME, exception);
		}
	}

	private static int clampInt(int value, int minValue, int maxValue) {
		return Math.max(minValue, Math.min(maxValue, value));
	}

	private static String stripJsonComments(String content) {
		StringBuilder builder = new StringBuilder();
		String[] lines = content.split("\\R", -1);
		for (String line : lines) {
			String trimmed = line.stripLeading();
			if (trimmed.startsWith("//")) {
				continue;
			}
			builder.append(line).append(System.lineSeparator());
		}
		return builder.toString();
	}

	private static String buildJsonConfigWithComments(JsonObject root) {
		String lineSeparator = System.lineSeparator();
		StringBuilder builder = new StringBuilder();
		builder.append("{").append(lineSeparator);
		builder.append("  // TrailsActive: when false, player trails are not generated or rendered.").append(lineSeparator);
		builder.append("  \"TrailsActive\": ").append(root.get("TrailsActive").getAsBoolean()).append(",").append(lineSeparator);
		builder.append(lineSeparator);
		builder.append("  // TrailsRenderCapes: enables or disables cape rendering on player trail ghosts.").append(lineSeparator);
		builder.append("  \"TrailsRenderCapes\": ").append(root.get("TrailsRenderCapes").getAsBoolean()).append(",").append(lineSeparator);
		builder.append(lineSeparator);
		builder.append("  // TrailsAmountLimit: maximum number of trails kept in memory.").append(lineSeparator);
		builder.append("  // When the limit is reached, the oldest trail is removed first.").append(lineSeparator);
		builder.append("  \"TrailsAmountLimit\": ").append(root.get("TrailsAmountLimit").getAsInt()).append(",").append(lineSeparator);
		builder.append(lineSeparator);
		builder.append("  // TrailsGenerationType: 'C' = continuous, 'S' = seconds.").append(lineSeparator);
		builder.append("  // C: generates trails continuously using TrailsContinuousTimings as the interval.").append(lineSeparator);
		builder.append("  // S: generates trails once per in-game second (optionally also at half-seconds).").append(lineSeparator);
		builder.append("  \"TrailsGenerationType\": \"").append(root.get("TrailsGenerationType").getAsString()).append("\",").append(lineSeparator);
		builder.append(lineSeparator);
		builder.append("  // TrailsContinuousTimings: time between generated trails (in seconds).").append(lineSeparator);
		builder.append("  // Only used when TrailsGenerationType=C.").append(lineSeparator);
		builder.append("  \"TrailsContinuousTimings\": ")
				.append(String.format(Locale.US, "%.2f", root.get("TrailsContinuousTimings").getAsDouble()))
				.append(",")
				.append(lineSeparator);
		builder.append(lineSeparator);
		builder.append("  // TrailsMidSecondsGeneration: if true and TrailsGenerationType=S,").append(lineSeparator);
		builder.append("  // also generates a trail at half-second intervals (2 trails per second).").append(lineSeparator);
		builder.append("  \"TrailsMidSecondsGeneration\": ").append(root.get("TrailsMidSecondsGeneration").getAsBoolean()).append(lineSeparator);
		builder.append("}").append(lineSeparator);
		return builder.toString();
	}

	private static double clampDouble(double value, double minValue, double maxValue) {
		return Math.max(minValue, Math.min(maxValue, value));
	}

	private static String normalizeGenerationType(String value) {
		if (value == null) {
			return DEFAULT_TRAILS_GENERATION_TYPE;
		}

		String normalized = value.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "S", "SECONDS" -> "S";
			case "C", "CONTINUOUS" -> "C";
			default -> DEFAULT_TRAILS_GENERATION_TYPE;
		};
	}
}
