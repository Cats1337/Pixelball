package io.github.cats1337.pixelball;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Pixelball implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Pixelball");
    public static Pixelball INSTANCE;

    private DonationBar donationBar;
    private MinecraftServer server;

    @Override
    public void onInitialize() {
        INSTANCE = this;
        PixelballConfig.load();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            LOGGER.info("[Pixelball] Server started, initializing donation bar...");
            PixelballUtils.setServer(server);
            createBossBar();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (donationBar != null) donationBar.addPlayer(handler.getPlayer());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DonationCommands.register(dispatcher);
        });
    }

    public void createBossBar() {
        PixelballConfig.reload();

        if (donationBar != null) {
            donationBar.attemptToCancel();
            donationBar.getBossBar().clearPlayers();
        }

        donationBar = new DonationBar();
        donationBar.createBar();

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

    public MinecraftServer getServer() {
        return server;
    }
}
