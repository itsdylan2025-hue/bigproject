package com.chunkfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans loaded client chunks for "marker" blocks. Each marker has a kind and a weight.
 * A chunk is flagged when the summed weight of a kind reaches the threshold picked by the
 * sensitivity setting. Raw scores are stored, so changing sensitivity applies instantly.
 */
public final class ActivityScanner {

	public enum Kind {
		GEODE("Amethyst geode"),
		SPAWNER("Spawner"),
		TRIAL_CHAMBER("Trial chamber"),
		STRONGHOLD("Stronghold portal room"),
		ANCIENT_CITY("Ancient city"),
		ACTIVITY("Player activity");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public record Result(Kind kind, int score, double y) {}

	public interface FlaggedConsumer {
		void accept(int chunkX, int chunkZ, Result result);
	}

	private record Marker(Kind kind, int weight) {}

	private record Hit(int cx, int cz, Result result, double distSq) {}

	// Index 0 = sensitivity 1 ... index 9 = sensitivity 10. Lower threshold = more sensitive.
	private static final int[] ACTIVITY_THRESHOLDS = {40, 30, 22, 16, 12, 9, 6, 4, 2, 1};
	private static final int[] STRUCTURE_THRESHOLDS = {10, 8, 6, 5, 4, 3, 3, 2, 2, 1};

	private static final Map<Block, Marker> MARKERS = new HashMap<>();

	private static void add(Block block, Kind kind, int weight) {
		MARKERS.put(block, new Marker(kind, weight));
	}

	static {
		// ---- Natural structures ----
		add(Blocks.BUDDING_AMETHYST, Kind.GEODE, 4);
		add(Blocks.AMETHYST_CLUSTER, Kind.GEODE, 2);
		add(Blocks.LARGE_AMETHYST_BUD, Kind.GEODE, 1);
		add(Blocks.MEDIUM_AMETHYST_BUD, Kind.GEODE, 1);
		add(Blocks.SMALL_AMETHYST_BUD, Kind.GEODE, 1);

		add(Blocks.SPAWNER, Kind.SPAWNER, 4);
		add(Blocks.TRIAL_SPAWNER, Kind.TRIAL_CHAMBER, 4);
		add(Blocks.VAULT, Kind.TRIAL_CHAMBER, 4);
		add(Blocks.END_PORTAL_FRAME, Kind.STRONGHOLD, 4);
		add(Blocks.REINFORCED_DEEPSLATE, Kind.ANCIENT_CITY, 1);
		add(Blocks.SCULK_SHRIEKER, Kind.ANCIENT_CITY, 2);

		// ---- Player activity (blocks that rarely generate in numbers) ----
		add(Blocks.CHEST, Kind.ACTIVITY, 2);
		add(Blocks.TRAPPED_CHEST, Kind.ACTIVITY, 3);
		add(Blocks.ENDER_CHEST, Kind.ACTIVITY, 4);
		add(Blocks.BARREL, Kind.ACTIVITY, 2);
		add(Blocks.FURNACE, Kind.ACTIVITY, 1);
		add(Blocks.BLAST_FURNACE, Kind.ACTIVITY, 2);
		add(Blocks.SMOKER, Kind.ACTIVITY, 2);
		add(Blocks.HOPPER, Kind.ACTIVITY, 3);
		add(Blocks.DISPENSER, Kind.ACTIVITY, 2);
		add(Blocks.DROPPER, Kind.ACTIVITY, 2);
		add(Blocks.CRAFTER, Kind.ACTIVITY, 3);
		add(Blocks.OBSERVER, Kind.ACTIVITY, 3);
		add(Blocks.PISTON, Kind.ACTIVITY, 2);
		add(Blocks.STICKY_PISTON, Kind.ACTIVITY, 3);
		add(Blocks.REPEATER, Kind.ACTIVITY, 2);
		add(Blocks.COMPARATOR, Kind.ACTIVITY, 3);
		add(Blocks.REDSTONE_LAMP, Kind.ACTIVITY, 2);
		add(Blocks.NOTE_BLOCK, Kind.ACTIVITY, 2);
		add(Blocks.JUKEBOX, Kind.ACTIVITY, 2);
		add(Blocks.TNT, Kind.ACTIVITY, 2);
		add(Blocks.CRAFTING_TABLE, Kind.ACTIVITY, 1);
		add(Blocks.LECTERN, Kind.ACTIVITY, 1);
		add(Blocks.ENCHANTING_TABLE, Kind.ACTIVITY, 3);
		add(Blocks.ANVIL, Kind.ACTIVITY, 2);
		add(Blocks.CHIPPED_ANVIL, Kind.ACTIVITY, 2);
		add(Blocks.DAMAGED_ANVIL, Kind.ACTIVITY, 2);
		add(Blocks.BEACON, Kind.ACTIVITY, 8);
		add(Blocks.CONDUIT, Kind.ACTIVITY, 8);
		add(Blocks.NETHER_PORTAL, Kind.ACTIVITY, 4);
		add(Blocks.LODESTONE, Kind.ACTIVITY, 4);
		add(Blocks.RESPAWN_ANCHOR, Kind.ACTIVITY, 5);
		add(Blocks.GLASS, Kind.ACTIVITY, 1);
		add(Blocks.IRON_BLOCK, Kind.ACTIVITY, 2);
		add(Blocks.GOLD_BLOCK, Kind.ACTIVITY, 2);
		add(Blocks.DIAMOND_BLOCK, Kind.ACTIVITY, 4);
		add(Blocks.EMERALD_BLOCK, Kind.ACTIVITY, 4);
		add(Blocks.NETHERITE_BLOCK, Kind.ACTIVITY, 6);

		Block[] beds = {
				Blocks.WHITE_BED, Blocks.ORANGE_BED, Blocks.MAGENTA_BED, Blocks.LIGHT_BLUE_BED,
				Blocks.YELLOW_BED, Blocks.LIME_BED, Blocks.PINK_BED, Blocks.GRAY_BED,
				Blocks.LIGHT_GRAY_BED, Blocks.CYAN_BED, Blocks.PURPLE_BED, Blocks.BLUE_BED,
				Blocks.BROWN_BED, Blocks.GREEN_BED, Blocks.RED_BED, Blocks.BLACK_BED
		};
		for (Block b : beds) {
			add(b, Kind.ACTIVITY, 1);
		}

		Block[] shulkers = {
				Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
				Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX,
				Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX,
				Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
				Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX,
				Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX
		};
		for (Block b : shulkers) {
			add(b, Kind.ACTIVITY, 5);
		}
	}

	private static final class Entry {
		final ChunkAccess chunk;
		final long tick;
		final int[] score;     // null when nothing interesting was found
		final double[] ySum;
		final int[] count;

		Entry(ChunkAccess chunk, long tick, int[] score, double[] ySum, int[] count) {
			this.chunk = chunk;
			this.tick = tick;
			this.score = score;
			this.ySum = ySum;
			this.count = count;
		}

		Result evaluate(ChunkFinderConfig cfg) {
			if (score == null) {
				return null;
			}
			Kind best = null;
			double bestRatio = 0;
			for (Kind k : Kind.values()) {
				int s = score[k.ordinal()];
				if (s <= 0) {
					continue;
				}
				if (k == Kind.ACTIVITY ? !cfg.detectActivity : !cfg.detectStructures) {
					continue;
				}
				int need = threshold(k, cfg.sensitivity);
				if (s < need) {
					continue;
				}
				double ratio = (double) s / need;
				if (best == null || ratio > bestRatio) {
					best = k;
					bestRatio = ratio;
				}
			}
			if (best == null) {
				return null;
			}
			int i = best.ordinal();
			return new Result(best, score[i], ySum[i] / count[i]);
		}
	}

	private static final int EXAMINE_PER_TICK = 48;

	private static final Map<Long, Entry> ENTRIES = new HashMap<>();
	private static final Set<Long> ANNOUNCED = new HashSet<>();
	private static ClientLevel lastLevel;
	private static long tickCounter;
	private static int[][] offsets = new int[0][];
	private static int offsetsRadius = -1;
	private static int cursor;

	private ActivityScanner() {}

	// ---------------------------------------------------------------- helpers

	static int threshold(Kind kind, int sensitivity) {
		int i = Math.max(1, Math.min(10, sensitivity)) - 1;
		return kind == Kind.ACTIVITY ? ACTIVITY_THRESHOLDS[i] : STRUCTURE_THRESHOLDS[i];
	}

	private static boolean isMarker(BlockState state) {
		return MARKERS.containsKey(state.getBlock());
	}

	private static long pack(int cx, int cz) {
		return ((long) cx & 0xFFFFFFFFL) << 32 | ((long) cz & 0xFFFFFFFFL);
	}

	private static int unpackX(long key) {
		return (int) (key >> 32);
	}

	private static int unpackZ(long key) {
		return (int) key;
	}

	private static void buildOffsets(int radius) {
		List<int[]> list = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz <= radius * radius) {
					list.add(new int[] {dx, dz});
				}
			}
		}
		list.sort(Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
		offsets = list.toArray(new int[0][]);
		offsetsRadius = radius;
		cursor = 0;
	}

	// ---------------------------------------------------------------- public API

	public static void reset() {
		ENTRIES.clear();
		ANNOUNCED.clear();
		cursor = 0;
	}

	public static void forEachFlagged(FlaggedConsumer consumer) {
		ChunkFinderConfig cfg = ChunkFinderConfig.get();
		for (Map.Entry<Long, Entry> e : ENTRIES.entrySet()) {
			Result r = e.getValue().evaluate(cfg);
			if (r != null) {
				long key = e.getKey();
				consumer.accept(unpackX(key), unpackZ(key), r);
			}
		}
	}

	public static int flaggedCount() {
		int[] n = {0};
		forEachFlagged((cx, cz, r) -> n[0]++);
		return n[0];
	}

	public static List<String> nearest(Minecraft mc, int max) {
		List<String> out = new ArrayList<>();
		if (mc.player == null) {
			return out;
		}
		double px = mc.player.getX() / 16.0;
		double pz = mc.player.getZ() / 16.0;
		List<Hit> hits = new ArrayList<>();
		forEachFlagged((cx, cz, r) -> {
			double dx = cx + 0.5 - px;
			double dz = cz + 0.5 - pz;
			hits.add(new Hit(cx, cz, r, dx * dx + dz * dz));
		});
		hits.sort(Comparator.comparingDouble(Hit::distSq));
		for (int i = 0; i < Math.min(max, hits.size()); i++) {
			Hit h = hits.get(i);
			out.add(h.result().kind().label() + " - chunk " + h.cx() + ", " + h.cz()
					+ " (x " + (h.cx() * 16) + ", z " + (h.cz() * 16) + ", y ~" + Math.round(h.result().y()) + ")");
		}
		return out;
	}

	// ---------------------------------------------------------------- scanning

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			reset();
			lastLevel = null;
			return;
		}
		if (level != lastLevel) {
			reset();
			lastLevel = level;
		}
		ChunkFinderConfig cfg = ChunkFinderConfig.get();
		if (!cfg.enabled) {
			return;
		}
		tickCounter++;

		int radius = cfg.simulationDistance;
		if (radius != offsetsRadius) {
			buildOffsets(radius);
		}
		if (offsets.length == 0) {
			return;
		}

		int pcx = Math.floorDiv((int) Math.floor(mc.player.getX()), 16);
		int pcz = Math.floorDiv((int) Math.floor(mc.player.getZ()), 16);
		ClientChunkCache cache = level.getChunkSource();
		int minY = level.dimensionType().minY();
		long rescanTicks = Math.max(20L, cfg.rescanSeconds * 20L);

		int examined = 0;
		int scanned = 0;
		int total = offsets.length;
		while (examined < EXAMINE_PER_TICK && examined < total && scanned < cfg.chunksPerTick) {
			int[] off = offsets[cursor];
			cursor = (cursor + 1) % total;
			examined++;

			int cx = pcx + off[0];
			int cz = pcz + off[1];
			long key = pack(cx, cz);

			ChunkAccess chunk = cache.getChunk(cx, cz, ChunkStatus.FULL, false);
			Entry old = ENTRIES.get(key);
			if (chunk == null) {
				if (old != null) {
					ENTRIES.remove(key);
				}
				continue;
			}
			boolean stale = old == null || old.chunk != chunk || tickCounter - old.tick >= rescanTicks;
			if (!stale) {
				continue;
			}

			Entry fresh = scan(chunk, minY, tickCounter);
			ENTRIES.put(key, fresh);
			scanned++;

			Result r = fresh.evaluate(cfg);
			if (r != null && cfg.notifyInChat && ANNOUNCED.add(key)) {
				announce(mc, cx, cz, r);
			}
		}

		if (tickCounter % 100 == 0) {
			int keep = radius + 2;
			ENTRIES.entrySet().removeIf(e -> Math.abs(unpackX(e.getKey()) - pcx) > keep
					|| Math.abs(unpackZ(e.getKey()) - pcz) > keep);
		}
	}

	private static Entry scan(ChunkAccess chunk, int minY, long tick) {
		LevelChunkSection[] sections = chunk.getSections();
		int kinds = Kind.values().length;
		int[] score = null;
		double[] ySum = null;
		int[] count = null;

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			// Palette check first: skips whole 16x16x16 sections with no interesting block.
			if (section == null || section.hasOnlyAir() || !section.maybeHas(ActivityScanner::isMarker)) {
				continue;
			}
			int baseY = minY + i * 16;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						Marker m = MARKERS.get(section.getBlockState(x, y, z).getBlock());
						if (m == null) {
							continue;
						}
						if (score == null) {
							score = new int[kinds];
							ySum = new double[kinds];
							count = new int[kinds];
						}
						int k = m.kind().ordinal();
						score[k] += m.weight();
						ySum[k] += baseY + y;
						count[k]++;
					}
				}
			}
		}
		return new Entry(chunk, tick, score, ySum, count);
	}

	private static void announce(Minecraft mc, int cx, int cz, Result r) {
		String text = "\u00a7d[Chunk Finder] \u00a7f" + r.kind().label()
				+ " \u00a77at chunk " + cx + ", " + cz
				+ " (x " + (cx * 16) + ", z " + (cz * 16) + ", y ~" + Math.round(r.y()) + ")";
		mc.player.displayClientMessage(Component.literal(text), false);
	}
           }
