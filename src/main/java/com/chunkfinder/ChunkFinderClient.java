package com.chunkfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkFinderClient implements ClientModInitializer {
	public static final String MOD_ID = "chunkfinder";
	public static final Logger LOGGER = LoggerFactory.getLogger("Chunk Finder");

	@Override
	public void onInitializeClient() {
		ChunkFinderConfig.load();
		ChunkRenderer.init();
		ClientTickEvents.END_CLIENT_TICK.register(ActivityScanner::tick);
		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess) -> ChunkFinderCommands.register(dispatcher));
		LOGGER.info("Chunk Finder loaded");
	}
}
