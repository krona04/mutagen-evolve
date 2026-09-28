package net.krona.mutagen.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.krona.mutagen.client.MutagenClient;

public final class MutagenFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MutagenClient.init();
        MutagenClient.registerScreens();
    }
}
