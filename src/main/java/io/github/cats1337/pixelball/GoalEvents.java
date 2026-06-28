package io.github.cats1337.pixelball;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static io.github.cats1337.pixelball.PixelballConfig.setDimensionEnabled;

public class GoalEvents {
    private final Map<Double, PixelballConfig.DonationData> donationActions = new LinkedHashMap<>();
    private final Map<Double, PixelballConfig.GoalData> goalActions = new LinkedHashMap<>();

    public GoalEvents() {
        loadConfig();
    }

    private void loadConfig() {
        donationActions.clear();
        goalActions.clear();
        donationActions.putAll(PixelballConfig.getDonations());
        goalActions.putAll(PixelballConfig.getGoals());
    }

    public void checkDonations(MinecraftServer server, double totalIncrease) {
        Pixelball.LOGGER.info("[Pixelball] Checking donations. Total increase: ${}", totalIncrease);

        try {
            JsonObject donorJson = DonationBar.requestDonorsJson(PixelballConfig.getCampaignId());
            JsonArray data = donorJson.getAsJsonArray("data");
            if (data == null) return;

            double remaining = totalIncrease;
            for (int i = 0; i < data.size() && remaining > 0.009; i++) {
                JsonObject donorData = data.get(i).getAsJsonObject();
                double donorAmount = donorData.getAsJsonObject("amount").get("value").getAsDouble();
                remaining -= donorAmount;
                processDonation(server, donorData, donorAmount);
            }
        } catch (IOException e) {
            Pixelball.LOGGER.warn("[Pixelball] Failed to fetch donor data: {}", e.getMessage());
        } catch (Exception e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to process donations", e);
        }
    }

    public void checkDonationsWithJson(MinecraftServer server, JsonObject donorData) {
        double donorAmount = donorData.getAsJsonObject("amount").get("value").getAsDouble();
        processDonation(server, donorData, donorAmount);
    }

    private void processDonation(MinecraftServer server, JsonObject donorData, double donorAmount) {
        String donorName = getString(donorData, "donor_name", "Anonymous");
        String comment = getString(donorData, "donor_comment", "");

        for (Map.Entry<Double, PixelballConfig.DonationData> entry : donationActions.entrySet()) {
            double threshold = entry.getKey();
            PixelballConfig.DonationData data = entry.getValue();
            if (donorAmount < threshold) continue;

            broadcast(server, "§aDonation from §e§n" + donorName + "§b of §6$" + format(donorAmount)
                    + (comment.isBlank() ? "" : " §7Message: §e\"" + comment + "\"")
                    + " §bExecuting Action: §a" + data.title());
            runAction(server, data.action());
        }
    }

    public void checkGoals(MinecraftServer server, double totalRaised) {
        Pixelball.LOGGER.info("[Pixelball] Checking goal thresholds with totalRaised = ${}", totalRaised);

        for (Map.Entry<Double, PixelballConfig.GoalData> entry : goalActions.entrySet()) {
            double key = entry.getKey();
            PixelballConfig.GoalData goal = entry.getValue();

            if (key > 0) {
                checkStaticGoal(server, key, goal, totalRaised);
            } else {
                checkRepeatGoal(server, key, goal, totalRaised);
            }
        }
    }

    private void checkStaticGoal(MinecraftServer server, double amount, PixelballConfig.GoalData goal, double totalRaised) {
        if (totalRaised < amount || goal.isReached()) return;

        broadcast(server, "§6Goal of §e$" + format(amount) + " §6reached! Executing Action: §a" + goal.getTitle());
        runAction(server, goal.getAction());
        PixelballConfig.setGoalReached(amount, true);
    }

private void checkRepeatGoal(MinecraftServer server, double key, PixelballConfig.GoalData goal, double totalRaised) {
    int interval = (int) -key;
    double lastReached = goal.getLastReached();
    double nextThreshold = lastReached + interval;

    if (totalRaised < nextThreshold) {
        return;
    }

    double newLastReached = Math.floor(totalRaised / interval) * interval;

    broadcast(server, "§6Repeatable goal of §e$" + format(newLastReached) + " §6reached! Executing Action: §a" + goal.getTitle());
    runAction(server, goal.getAction());
    PixelballConfig.setRepeatGoalLastReached(interval, newLastReached);
}


    public void runAction(MinecraftServer server, String action) {
        if (action == null || action.isBlank()) return;

        String[] parts = action.trim().split("\\s+");
        try {
            switch (parts[0].toLowerCase(Locale.ROOT)) {
                case "spawnpokemon" -> spawnPokemonForEachPlayer(server, parts);
                case "give" -> giveForEachPlayer(server, action);
                case "random_stone" -> giveRandomStone(server);
                case "random_held_item" -> giveRandomHeldItem(server);
                case "random_pokeball" -> giveRandomPokeball(server);
                case "enable_nether" -> {
                    broadcast(server, "§aGoal Reached! §cNether §aEnabled!");
                    setDimensionEnabled("nether", true);
                }
                case "enable_end" -> {
                    broadcast(server, "§aGoal Reached! §cThe End §aEnabled!");
                    setDimensionEnabled("end", true);
                }
                case "legendaryspawn" -> spawnLegendaries(server, parts);
                case "command" -> executeRawCommand(server, action.substring("command".length()).trim());
                default -> broadcast(server, "§cInvalid Pixelball action: " + parts[0]);
            }
        } catch (Exception e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to run action '{}': {}", action, e.getMessage());
            broadcast(server, "§cPixelball action failed: §f" + action);
        }
    }

    private void spawnPokemonForEachPlayer(MinecraftServer server, String[] parts) {
        if (parts.length < 3) throw new IllegalArgumentException("spawnpokemon requires rarity and pokemon");

        String rarity = parts[1];
        String pokemon = parts[2];
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            BlockPos pos = player.getBlockPos().up();
            execute(server, String.format("spawnpokemonat %d %d %d %s %s", pos.getX(), pos.getY(), pos.getZ(), pokemon, rarity));
        }
    }

    private void giveForEachPlayer(MinecraftServer server, String action) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            execute(server, action.replace("player", player.getName().getString()));
        }
    }

    private void giveRandomStone(MinecraftServer server) {
        List<String> stones = PixelballConfig.getStones();

        if (stones.isEmpty()) {
            Pixelball.LOGGER.warn("[Pixelball] No stones configured.");
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            execute(server, "give " + player.getName().getString() + " cobblemon:" + randomFrom(stones) + " 1");
        }
    }

    private void giveRandomHeldItem(MinecraftServer server) {
        List<String> heldItems = PixelballConfig.getHeldItems();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            execute(server, "give " + player.getName().getString() + " cobblemon:" + randomFrom(heldItems) + " 1");
        }
    }

    private void giveRandomPokeball(MinecraftServer server) {
        List<String> pokeballs = PixelballConfig.getPokeballs();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            execute(server, "give " + player.getName().getString() + " cobblemon:" + randomFrom(pokeballs) + " 1");
        }
    }

    private void spawnLegendaries(MinecraftServer server, String[] parts) {
        int count = parts.length >= 2 ? Integer.parseInt(parts[1]) : 1;

        Map<String, Integer> legendaries = PixelballConfig.getLegendaries();

        if (legendaries.isEmpty()) {
            Pixelball.LOGGER.warn("[Pixelball] No legendaries configured.");
            return;
        }

        var world = server.getOverworld();
        var border = world.getWorldBorder();

        int minX = (int) (border.getCenterX() - border.getSize() / 2);
        int maxX = (int) (border.getCenterX() + border.getSize() / 2);
        int minZ = (int) (border.getCenterZ() - border.getSize() / 2);
        int maxZ = (int) (border.getCenterZ() + border.getSize() / 2);

        var biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(legendaries.entrySet());

        for (int i = 0; i < count; i++) {
            int x = randomBetween(minX, maxX);
            int z = randomBetween(minZ, maxZ);
            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) + 5;
            BlockPos pos = new BlockPos(x, y, z);

            String biomeName = biomeRegistry.getKey(world.getBiome(pos).value())
                    .map(key -> key.getValue().getPath().replace("_", " "))
                    .orElse("unknown");

            int hintX = x + randomBetween(-500, 500);
            int hintZ = z + randomBetween(-500, 500);

            Map.Entry<String, Integer> legendaryEntry =
                    entries.get(ThreadLocalRandom.current().nextInt(entries.size()));

            String pokemon = legendaryEntry.getKey();
            int level = legendaryEntry.getValue();

            String legendary = pokemon + " lvl=" + level;

            broadcast(server, "§d§k! §6Legendary §l" + pokemon
                    + "§r §ahas appeared somewhere near §e~" + hintX + ", ~" + hintZ
                    + "§a in a §b" + biomeName + "§a biome!");

            execute(server, String.format(
                    "spawnpokemonat %d %d %d %s lvl=%d",
                    x,
                    y,
                    z,
                    pokemon,
                    level
            ));

            PixelballDebug.send(
                    server,
                    "Spawned legendary '" + legendary + "' at " +
                            "world=overworld, x=" + x + ", y=" + y + ", z=" + z +
                            ", biome=" + biomeName +
                            ", publicHint=~" + hintX + ", ~" + hintZ
            );
        }
    }

    private void executeRawCommand(MinecraftServer server, String command) {
        if (command.isBlank()) throw new IllegalArgumentException("command action requires a command");
        execute(server, command);
    }

    private void execute(MinecraftServer server, String command) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
    }

    private void broadcast(MinecraftServer server, String message) {
        server.getPlayerManager().broadcast(Text.of(message), false);
    }

    private static String getString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) return fallback;
        return object.get(key).getAsString();
    }

    private static String format(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.format(Locale.US, "%.2f", number);
    }

    private String randomFrom(List<String> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private static int randomBetween(int minInclusive, int maxExclusive) {
        if (maxExclusive <= minInclusive) return minInclusive;
        return ThreadLocalRandom.current().nextInt(minInclusive, maxExclusive);
    }
}
