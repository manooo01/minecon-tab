package dev.minecontab;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Purely visual client mod. It sends no packets, makes no network requests,
 * and only changes how tab-list names are drawn on YOUR screen.
 */
public class MineconTabClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Forget cached results when leaving a server.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CapeDetector.clear());
    }
}
