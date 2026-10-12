package com.chunkfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saved to config/chunkfinder.json */
public final class ChunkFinderConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ChunkFinderConfig instance = new ChunkFinderConfig();

	public boolean enabled = true;
	/** 1 = only obvious finds, 10 = flag chunks on the slightest evidence. */
	public int sensitivity = 5;
	/** Scan radius in chunks around you (the client can only scan chunks the server sent). */
	public int simulationDistance = 8;
	/** Colour for amethyst geodes. */
	public String color = "#F050F8";
	/** Colour for every other flagged chunk. */
	public String otherColor = "#FF0000";
	public float fillAlpha = 0.30f;
	public float edgeAlpha = 0.90f;
	public float wallAlpha = 0.40f;
	public float wallHeight = 6.0f;
	public boolean throughWalls = true;
	public boolean detectStructures = true;
	public boolean detectActivity = true;
	public boolean notifyInChat = true;
	public int chunksPerTick = 2;
	public int rescanSeconds = 15;

	private transient String cachedGeodeHex;
	private transient float[] cachedGeodeRgb;
	private transient String cachedOtherHex;
	private transient float[] cachedOtherRgb;

	public static ChunkFinderConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("chunkfinder.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p)) {
				ChunkFinderConfig loaded = GSON.fromJson(r, ChunkFinderConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				ChunkFinderClient.LOGGER.warn("Could not read chunkfinder.json, using defaults", e);
			}
		}
		instance.clamp();
		instance.save();
	}

	public void save() {
		try (Writer w = Files.newBufferedWriter(path())) {
			GSON.toJson(this, w);
		} catch (IOException e) {
			ChunkFinderClient.LOGGER.warn("Could not save chunkfinder.json", e);
		}
	}

	public void clamp() {
		sensitivity = Math.max(1, Math.min(10, sensitivity));
		simulationDistance = Math.max(2, Math.min(32, simulationDistance));
		fillAlpha = Math.max(0f, Math.min(1f, fillAlpha));
		edgeAlpha = Math.max(0f, Math.min(1f, edgeAlpha));
		wallAlpha = Math.max(0f, Math.min(1f, wallAlpha));
		wallHeight = Math.max(0f, Math.min(64f, wallHeight));
		chunksPerTick = Math.max(1, Math.min(16, chunksPerTick));
		rescanSeconds = Math.max(2, Math.min(300, rescanSeconds));
		if (color == null) {
			color = "#F050F8";
		}
		if (otherColor == null) {
			otherColor = "#FF0000";
		}
	}

	/** Geode colour as 0-1 floats (r, g, b). Falls back to #F050F8 if the hex is invalid. */
	public float[] geodeRgb() {
		if (cachedGeodeRgb == null || !color.equals(cachedGeodeHex)) {
			cachedGeodeHex = color;
			cachedGeodeRgb = parseHex(color, 0xF050F8);
		}
		return cachedGeodeRgb;
	}

	/** Colour for non-geode chunks. Falls back to red if the hex is invalid. */
	public float[] otherRgb() {
		if (cachedOtherRgb == null || !otherColor.equals(cachedOtherHex)) {
			cachedOtherHex = otherColor;
			cachedOtherRgb = parseHex(otherColor, 0xFF0000);
		}
		return cachedOtherRgb;
	}

	private static float[] parseHex(String text, int fallback) {
		String hex = text.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		int v = fallback;
		try {
			if (hex.length() == 6) {
				v = Integer.parseInt(hex, 16);
			}
		} catch (NumberFormatException ignored) {
		}
		return new float[] {((v >> 16) & 0xFF) / 255f, ((v >> 8) & 0xFF) / 255f, (v & 0xFF) / 255f};
	}
}
