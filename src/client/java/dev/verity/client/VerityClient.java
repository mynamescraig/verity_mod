package dev.verity.client;

import dev.verity.VerityMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class VerityClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(VerityMod.VERITY, VerityRenderer::new);
	}
}
