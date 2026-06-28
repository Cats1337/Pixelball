package io.github.cats1337.pixelball;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PixelballBypass {
    private static final Set<UUID> NetherBypass = new HashSet<>();
    private static final Set<UUID> EndBypass = new HashSet<>();

    private PixelballBypass() {
    }

    public static boolean toggleNether(ServerPlayerEntity player) {
        return toggle(NetherBypass, player.getUuid());
    }

    public static boolean toggleEnd(ServerPlayerEntity player) {
        return toggle(EndBypass, player.getUuid());
    }

    public static boolean canBypassNether(ServerPlayerEntity player) {
        return NetherBypass.contains(player.getUuid());
    }

    public static boolean canBypassEnd(ServerPlayerEntity player) {
        return EndBypass.contains(player.getUuid());
    }

    private static boolean toggle(Set<UUID> set, UUID uuid) {
        if (set.contains(uuid)) {
            set.remove(uuid);
            return false;
        }

        set.add(uuid);
        return true;
    }
}