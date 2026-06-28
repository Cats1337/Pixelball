package io.github.cats1337.pixelball;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Pixelball implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(Pixelball.class);
    public static Pixelball INSTANCE;
    public static final String MOD_ID = "pixelball";
    public static final String MOD_VERSION =
            FabricLoader.getInstance()
                    .getModContainer(MOD_ID)
                    .orElseThrow()
                    .getMetadata()
                    .getVersion()
                    .getFriendlyString();

    private DonationBar donationBar;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        INSTANCE = this;

        try {
            PixelballConfig.load();
        } catch (IOException e) {
            LOGGER.error("[Pixelball] Failed to load config. Pixelball will not start.", e);
            return;
        }

        PixelballPlaceholders.register();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            LOGGER.info("[Pixelball] Server started, initializing donation bar...");
            PixelballUtils.setServer(server);
            try {
                createBossBar();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (donationBar != null) donationBar.addPlayer(handler.getPlayer());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> DonationCommands.register(dispatcher));
    }

    public void createBossBar() throws IOException {
        PixelballConfig.reload();

        if (donationBar != null) {
            donationBar.attemptToCancel();
        }

        donationBar = new DonationBar();
        donationBar.start();

        // Add all online players
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                donationBar.addPlayer(player);
            }
        }

    }

    public DonationBar getDonationBar() {
        return donationBar;
    }

}
