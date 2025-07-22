package io.github.cats1337.pixelball;

import com.google.common.net.HttpHeaders;
import com.google.gson.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import static io.github.cats1337.pixelball.DonationBar.requestToken;

public class GoalEvents {
    private final Map<Double, String> donationActions = new HashMap<>();
    private final Map<Double, GoalAction> goalActions = new HashMap<>();

    public GoalEvents() {
        loadConfig();
    }

    private void loadConfig() {
        PixelballConfig.getDonations().forEach((key, val) -> {
            donationActions.put(key, val.action());
        });

        PixelballConfig.getGoals().forEach((key, goalData) -> {
            goalActions.put(key, new GoalAction(goalData.getAction(), goalData.isReached(), goalData.getLastReached()));
        });
    }

    public void loadDonationActions() {
        donationActions.clear();
        PixelballConfig.getDonations().forEach((key, val) -> {
            donationActions.put(key, val.action());
        });
    }

//    get donation acition title
    public String getDonationActionTitle(double key) {
        return PixelballConfig.getDonations().get(key).title();
    }


    public void checkDonations(MinecraftServer server, double amount) {
        Pixelball.LOGGER.info("[Pixelball] Checking donations... Total increase: $" + amount);

        try {
            JsonObject donorJson = requestDonorsJson(PixelballConfig.getCampaignId());
            JsonArray data = donorJson.get("data").getAsJsonArray();
            double remaining = amount;

            for (int i = 0; i < Math.min(data.size(), 3); i++) {
                if (remaining <= 0) break;
                JsonObject donorData = data.get(i).getAsJsonObject();
                double donorAmount = donorData.get("amount").getAsJsonObject().get("value").getAsDouble();
                String donorName = donorData.get("donor_name").getAsString();
                String comment = donorData.get("donor_comment").isJsonNull() ? "" : donorData.get("donor_comment").getAsString();

                System.out.printf("[Pixelball] Donor: %s, Amount: %.2f, Message: %s%n", donorName, donorAmount, comment);
                remaining -= donorAmount;

                for (Map.Entry<Double, String> entry : donationActions.entrySet()) {
                    if (donorAmount >= entry.getKey()) {
                        String action = entry.getValue();
                        String title = PixelballConfig.getDonations().get(entry.getKey()).title();

                        broadcast(server,"§aDonation from §e§n" + donorName + "§b of §6$" + donorAmount +
                                (comment.isEmpty() ? "§7They left no message" : " §7Their message: §e\"" + comment + "\"") + " §bExecuting Action: §a" + title);

                        runAction(server, action);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("[Pixelball] Failed to fetch donor data");
            e.printStackTrace();
        }
    }

    public void checkGoals(MinecraftServer server, double totalRaised) {
        Pixelball.LOGGER.info("[Pixelball] Checking goal thresholds with totalRaised = $" + totalRaised);
        for (Map.Entry<Double, GoalAction> entry : goalActions.entrySet()) {
            double key = entry.getKey();
            GoalAction goalAction = entry.getValue();

            if (key > 0) {
                if (totalRaised >= key && !goalAction.isReached()) {
                    String title = getDonationActionTitle(key);
                    broadcast(server, "§6Goal of §e$" + key + " §6reached! Executing Action: §a" + title);
                    goalAction.setReached(true);
                    runAction(server, goalAction.getAction());
                    PixelballConfig.setGoalReached(key, true);
                }
            } else {
                int interval = (int) -key;
                double last = PixelballConfig.getGoals().get(key).getLastReached();

                while (totalRaised >= last + interval) {
                    last += interval;
                    String title = getDonationActionTitle(key);
                    broadcast(server, "§6Repeatable Goal of §e$" + last + " §6reached! Executing Action: §a" + title);
                    runAction(server, goalAction.getAction());
                    PixelballConfig.setRepeatGoalLastReached(interval, last);
                }
            }
        }
    }

    private void runAction(MinecraftServer server, String action) {
        String[] parts = action.split(" ");
        switch (parts[0]) {
            case "spawnpokemon" -> {
                String rarity = parts[1];
                String pokemon = parts[2];
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    BlockPos pos = player.getBlockPos().up();
                    String cmd = String.format("spawnpokemonat %d %d %d %s %s",
                            pos.getX(), pos.getY(), pos.getZ(), pokemon, rarity);
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
                }
            }
            case "give" -> {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    String cmd = action.replace("player", player.getName().getString());
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
                }
            }


            case "random_stone" -> {
                String[] stones = {
                        "fire_stone", "water_stone", "thunder_stone", "leaf_stone", "moon_stone", "sun_stone",
                        "shiny_stone", "dusk_stone", "dawn_stone", "ice_stone", "oval_stone"
                };

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    String stone = stones[(int) (Math.random() * stones.length)];
                    String cmd = "give " + player.getName().getString() + " cobblemon:" + stone + " 1";
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
                }
            }

            case "random_held_item" -> {
                String[] heldItems = {
                        "ability_shield", "absorb_bulb", "air_balloon", "assault_vest", "big_root", "binding_band",
                        "black_sludge", "blunder_policy", "bright_powder", "black_belt", "black_glasses", "cell_battery",
                        "choice_band", "choice_scarf", "choice_specs", "cleanse_tag", "covert_cloak", "charcoal_stick",
                        "damp_rock", "deep_sea_scale", "deep_sea_tooth", "destiny_knot", "dragon_fang", "eject_button",
                        "eject_pack", "everstone", "eviolite", "expert_belt", "exp_share", "flame_orb", "float_stone",
                        "focus_band", "focus_sash", "fairy_feather", "heat_rock", "hard_stone", "icy_rock", "iron_ball",
                        "kings_rock", "leftovers", "life_orb", "light_ball", "light_clay", "loaded_dice", "lucky_egg",
                        "medicinal_leek", "mental_herb", "metal_powder", "metronome", "mirror_herb", "muscle_band",
                        "magnet", "metal_coat", "miracle_seed", "mystic_water", "never_melt_ice", "power_herb",
                        "punching_glove", "protective_pads", "poison_barb", "power_anklet", "power_band", "power_belt",
                        "power_bracer", "power_lens", "power_weight", "quick_claw", "quick_powder", "razor_claw",
                        "razor_fang", "red_card", "ring_target", "rocky_helmet", "room_service", "safety_goggles",
                        "scope_lens", "shed_shell", "shell_bell", "smoke_ball", "smooth_rock", "soothe_bell",
                        "sticky_barb", "sharp_beak", "silk_scarf", "silver_powder", "soft_sand", "spell_tag",
                        "terrain_extender", "throat_spray", "toxic_orb", "twisted_spoon", "utility_umbrella",
                        "weakness_policy", "white_herb", "wide_lens", "wise_glasses", "zoom_lens"
                };

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    String item = heldItems[(int) (Math.random() * heldItems.length)];
                    String cmd = "give " + player.getName().getString() + " cobblemon:" + item + " 1";
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
                }
            }

            case "enablenether" -> broadcast(server, "§aGoal Reached! §cNether §aEnabled!");
            case "legendaryspawn" -> {
                int count = Integer.parseInt(parts[1]);
                String[] legendaries = {"articuno lvl=55", "moltres lvl=55", "zapdos lvl=55", "mewtwo lvl=70"};

                var world = server.getOverworld();
                var border = world.getWorldBorder();

                int minX = (int) (border.getCenterX() - border.getSize() / 2);
                int maxX = (int) (border.getCenterX() + border.getSize() / 2);
                int minZ = (int) (border.getCenterZ() - border.getSize() / 2);
                int maxZ = (int) (border.getCenterZ() + border.getSize() / 2);

                var biomeRegistry = world.getRegistryManager().get(RegistryKeys.BIOME);

                for (int i = 0; i < count; i++) {
                    int x = minX + (int)(Math.random() * (maxX - minX));
                    int z = minZ + (int)(Math.random() * (maxZ - minZ));
                    int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) + 5;
                    BlockPos pos = new BlockPos(x, y, z);

                    Pixelball.LOGGER.info("[Pixelball] Spawning legendary at: " + pos);

                    var biomeKey = biomeRegistry.getKey(world.getBiome(pos).value())
                            .orElseThrow()
                            .getValue()
                            .getPath()
                            .replace("_", " ");

                    // Fake "hinted" location ±500
                    int hintX = x + (int)(Math.random() * 1000) - 500;
                    int hintZ = z + (int)(Math.random() * 1000) - 500;

                    String legendary = legendaries[i % legendaries.length];
                    String cmd = String.format("spawnpokemonat %d %d %d %s", x, y, z, legendary);

                    broadcast(server, "§d§k! §6Legendary §l" + legendary +
                            "§r §ahas appeared somewhere near §e~" + hintX + ", ~" + hintZ +
                            "§a in a §b" + biomeKey + "§a biome!");
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
                }
            }
            default -> broadcast(server, "Invalid action: " + parts[0]);
        }
    }

    private void broadcast(MinecraftServer server, String message) {
        server.getPlayerManager().broadcast(Text.of(message), false);
    }

    public void checkDonationsWithJson(MinecraftServer server, JsonObject donorData) {
        Pixelball.LOGGER.info("[Pixelball] Checking donations with JSON data...");

        if (donationActions.isEmpty()) {
            Pixelball.LOGGER.warn("[Pixelball] donationActions is empty — reloading...");
            loadDonationActions();
        }

        double donorAmount = donorData.get("amount").getAsJsonObject().get("value").getAsDouble();
        for (Map.Entry<Double, String> entry : donationActions.entrySet()) {
            if (donorAmount >= entry.getKey()) {
                String action = entry.getValue();
                String donorName = donorData.get("donor_name").getAsString();
                String comment = donorData.get("donor_comment").isJsonNull() ? "" : donorData.get("donor_comment").getAsString();

                server.getPlayerManager().broadcast(Text.of("§bDonation from §e" + donorName + "§b of §6$" + donorAmount +
                        (comment.isEmpty() ? "" : " §7| §f\"" + comment + "\"") + " §b=> Executing: §a" + action), false);

                runAction(server, action);
            }
        }
    }



    private static class GoalAction {
        private final String action;
        private boolean reached;
        private double lastReached;

        public GoalAction(String action, boolean reached) {
            this(action, reached, 0);
        }

        public GoalAction(String action, boolean reached, double lastReached) {
            this.action = action;
            this.reached = reached;
            this.lastReached = lastReached;
        }

        public String getAction() {
            return action;
        }

        public boolean isReached() {
            return reached;
        }

        public void setReached(boolean reached) {
            this.reached = reached;
        }

        public double getLastReached() {
            return lastReached;
        }

        public void setLastReached(double lastReached) {
            this.lastReached = lastReached;
        }
    }


    private JsonObject requestDonorsJson(String id) throws IOException {
        URL url = new URL("https://v5api.tiltify.com/api/public/team_campaigns/" + id + "/donations?limit=3");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String token = requestToken().getAsJsonObject().get("access_token").getAsString();
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
