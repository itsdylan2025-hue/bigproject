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
	public String color = "#F050F8";
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

	private transient String cachedColor;
	private transient float[] cachedRgb;

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
	}

	/** Highlight colour as 0-1 floats (r, g, b). Falls back to #F050F8 if the hex is invalid. */
	public float[] rgb() {
		if (cachedRgb != null && color.equals(cachedColor)) {
			return cachedRgb;
		}
		String hex = color.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		int v = 0xF050F8;
		try {
			if (hex.length() == 6) {
				v = Integer.parseInt(hex, 16);
			}
		} catch (NumberFormatException ignored) {
		}
		cachedColor = color;
		cachedRgb = new float[] {((v >> 16) & 0xFF) / 255f, ((v >> 8) & 0xFF) / 255f, (v & 0xFF) / 255f};
		return cachedRgb;
	}
}
