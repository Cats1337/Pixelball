package io.github.cats1337.pixelball;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PixelballDebug {
    private static final Set<UUID> DebugPlayers = new HashSet<>();

    private PixelballDebug() {
    }

    public static boolean toggle(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (DebugPlayers.contains(uuid)) {
            DebugPlayers.remove(uuid);
            return false;
        }

        DebugPlayers.add(uuid);
        return true;
    }

    public static void send(MinecraftServer server, String message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (DebugPlayers.contains(player.getUuid())) {
                player.sendMessage(Text.literal("§8[§dPixelball Debug§8] §f" + message), false);
            }
        }
    }
}