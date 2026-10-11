package com.chunkfinder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class ChunkFinderCommands {
	private ChunkFinderCommands() {}

	private static void say(FabricClientCommandSource src, String text) {
		src.sendFeedback(Component.literal("\u00a7d[Chunk Finder] \u00a7f" + text));
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommandManager.literal("chunkfinder")
				.executes(ctx -> {
					ChunkFinderConfig c = ChunkFinderConfig.get();
					say(ctx.getSource(), (c.enabled ? "ON" : "OFF")
							+ " | sensitivity " + c.sensitivity + "/10"
							+ " | distance " + c.simulationDistance + " chunks"
							+ " | colour " + c.color
							+ " | through walls " + c.throughWalls
							+ " | flagged chunks " + ActivityScanner.flaggedCount());
					return 1;
				})
				.then(ClientCommandManager.literal("toggle").executes(ctx -> {
					ChunkFinderConfig c = ChunkFinderConfig.get();
					c.enabled = !c.enabled;
					c.save();
					if (!c.enabled) {
						ActivityScanner.reset();
					}
					say(ctx.getSource(), c.enabled ? "Enabled" : "Disabled");
					return 1;
				}))
				.then(ClientCommandManager.literal("sensitivity")
						.then(ClientCommandManager.argument("level", IntegerArgumentType.integer(1, 10))
								.executes(ctx -> {
									ChunkFinderConfig c = ChunkFinderConfig.get();
									c.sensitivity = IntegerArgumentType.getInteger(ctx, "level");
									c.save();
									say(ctx.getSource(), "Sensitivity set to " + c.sensitivity
											+ "/10 (higher = flags chunks with less evidence)");
									return 1;
								})))
				.then(ClientCommandManager.literal("distance")
						.then(ClientCommandManager.argument("chunks", IntegerArgumentType.integer(2, 32))
								.executes(ctx -> {
									ChunkFinderConfig c = ChunkFinderConfig.get();
									c.simulationDistance = IntegerArgumentType.getInteger(ctx, "chunks");
									c.save();
									say(ctx.getSource(), "Scan distance set to " + c.simulationDistance + " chunks");
									return 1;
								})))
				.then(ClientCommandManager.literal("walls").executes(ctx -> {
					ChunkFinderConfig c = ChunkFinderConfig.get();
					c.throughWalls = !c.throughWalls;
					c.save();
					say(ctx.getSource(), "Highlight through walls: " + c.throughWalls);
					return 1;
				}))
				.then(ClientCommandManager.literal("notify").executes(ctx -> {
					ChunkFinderConfig c = ChunkFinderConfig.get();
					c.notifyInChat = !c.notifyInChat;
					c.save();
					say(ctx.getSource(), "Chat alerts: " + c.notifyInChat);
					return 1;
				}))
				.then(ClientCommandManager.literal("color")
						.then(ClientCommandManager.argument("hex", StringArgumentType.word())
								.executes(ctx -> {
									String hex = StringArgumentType.getString(ctx, "hex");
									if (hex.startsWith("#")) {
										hex = hex.substring(1);
									}
									if (!hex.matches("[0-9a-fA-F]{6}")) {
										say(ctx.getSource(), "Use a 6-digit hex colour, e.g. F050F8");
										return 0;
									}
									ChunkFinderConfig c = ChunkFinderConfig.get();
									c.color = "#" + hex.toUpperCase();
									c.save();
									say(ctx.getSource(), "Colour set to " + c.color);
									return 1;
								})))
				.then(ClientCommandManager.literal("list").executes(ctx -> {
					List<String> lines = ActivityScanner.nearest(Minecraft.getInstance(), 8);
					if (lines.isEmpty()) {
						say(ctx.getSource(), "No flagged chunks right now");
					} else {
						for (String line : lines) {
							say(ctx.getSource(), line);
						}
					}
					return 1;
				}))
				.then(ClientCommandManager.literal("clear").executes(ctx -> {
					ActivityScanner.reset();
					say(ctx.getSource(), "Cleared - rescanning");
					return 1;
				})));
	}
                                                              }
