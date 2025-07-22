package io.github.cats1337.pixelball;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;

import java.util.LinkedHashMap;
import java.util.Map;

public class PixelballConfig {
    private static ConfigurationNode root;
    private static File configFile;

    public static void load() {
        File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "pixelball");
        if (!configDir.exists()) configDir.mkdirs();
        configFile = new File(configDir, "config.yml");

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configFile.toPath())
                .build();

        try {
            if (!configFile.exists()) {
                configFile.createNewFile();
                root = loader.createNode();
                saveDefaults();
                loader.save(root);
            }
            root = loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    public static Map<Double, DonationData> getDonations() {
        Map<Double, DonationData> donations = new LinkedHashMap<>();
        ConfigurationNode node = getDonationsNode();

        node.childrenMap().forEach((key, value) -> {
            try {
                double amount = Double.parseDouble(key.toString());
                String action = value.node("action").getString();
                String title = value.node("title").getString();
                if (action != null && title != null) {
                    donations.put(amount, new DonationData(action, title));
                }
            } catch (NumberFormatException ignored) {}
        });

        return donations;
    }

    public static Map<Double, GoalData> getGoals() {
        Map<Double, GoalData> goals = new LinkedHashMap<>();
        ConfigurationNode node = getGoalsNode();

        node.childrenMap().forEach((key, value) -> {
            String keyStr = key.toString();
            try {
                // Static goal like 250, 500
                double amount = Double.parseDouble(keyStr);
                String action = value.node("action").getString();
                String title = value.node("title").getString();
                boolean reached = value.node("reached").getBoolean(false);
                if (action != null && title != null) {
                    goals.put(amount, new GoalData(action, title, reached, 0, 0));
                }
            } catch (NumberFormatException e) {
                // Handle repeating goal like "repeat-100"
                if (keyStr.startsWith("repeat-")) {
                    int interval = Integer.parseInt(keyStr.substring("repeat-".length()));
                    String action = value.node("action").getString();
                    String title = value.node("title").getString();
                    double lastReached = value.node("last_reached").getDouble(0);
                    if (action != null && title != null) {
                        goals.put((double) -interval, new GoalData(action, title, false, interval, lastReached));
                    }
                }
            }
        });

        return goals;
    }


    public static void setGoalReached(double amount, boolean reached) {
        String key = String.valueOf((int) amount);
        if (!getGoalsNode().childrenMap().containsKey(key)) return; // skip if goal not defined

        ConfigurationNode node = getGoalsNode().node(key).node("reached");
        try {
            node.set(reached);
            YamlConfigurationLoader.builder().path(configFile.toPath()).build().save(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void setRepeatGoalLastReached(int interval, double amount) {
        ConfigurationNode node = getGoalsNode().node("repeat-" + interval).node("last_reached");
        try {
            node.set(amount);
            YamlConfigurationLoader.builder().path(configFile.toPath()).build().save(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getBarUpdateInterval() {
        int interval = root.node("update-interval-seconds").getInt(30);
        return Math.max(interval, 5); // Enforce a hard lower limit for safety
    }

    public record DonationData(String action, String title) {}

    public static class GoalData {
        private final String action;
        private final String title;
        private final boolean reached;
        private final int interval;
        private double lastReached;

        public GoalData(String action, String title, boolean reached, int interval, double lastReached) {
            this.action = action;
            this.title = title;
            this.reached = reached;
            this.interval = interval;
            this.lastReached = lastReached;
        }

        public String getAction() { return action; }
        public String getTitle() { return title; }
        public boolean isReached() { return reached; }
        public int getInterval() { return interval; }
        public double getLastReached() { return lastReached; }

        public void setLastReached(double amount) { this.lastReached = amount; }

        @Override
        public String toString() {
            return "GoalData{title='%s', reached=%s, interval=%d, lastReached=%.2f}"
                    .formatted(title, reached, interval, lastReached);
        }
    }

    private static void saveDefaults() throws IOException {
        root.node("client-id").set("");
        root.node("client-secret").set("");
        root.node("campaign-id").set("");
        root.node("total_amount_raised").set(0);
        root.node("main-title-color").set("&d");
        root.node("main-bar-color").set("WHITE");
        root.node("goal-message").set("Goal Reached!");
        root.node("goal-title-color").set("&f");
        root.node("goal-bar-color").set("BLUE");
        root.node("update-interval-seconds").set(30);

        ConfigurationNode donations = root.node("donations");
        donations.node("5").node("action").set("give player cobblemon:poke_ball 1");
        donations.node("5").node("title").set("Pokeball");

        donations.node("10").node("action").set("random_stone 1");
        donations.node("10").node("title").set("Random Pokemon Stone");

        donations.node("25").node("action").set("random_held_item 1");
        donations.node("25").node("title").set("Random Held Item");

        donations.node("50").node("action").set("spawnpokemon shiny random");
        donations.node("50").node("title").set("Eevee Shiny");

        ConfigurationNode goals = root.node("goals");
        goals.node("250").node("action").set("enablenether");
        goals.node("250").node("title").set("Enable Nether");
        goals.node("250").node("reached").set(false);

        goals.node("500").node("action").set("legendaryspawn 3");
        goals.node("500").node("title").set("Random Legendaries");
        goals.node("500").node("reached").set(false);

        // Repeatable goal for spawning random shiny Pokémon
        goals.node("repeat-100").node("action").set("spawnpokemon shiny random");
        goals.node("repeat-100").node("title").set("Random Shiny");
        goals.node("repeat-100").node("interval").set(100);
        goals.node("repeat-100").node("last_reached").set(0);
    }

    public static void reload() {
        load();
    }

    public static String getClientId() {
        return root.node("client-id").getString("");
    }

    public static String getClientSecret() {
        return root.node("client-secret").getString("");
    }

    public static String getCampaignId() {
        return root.node("campaign-id").getString("");
    }

    public static double getTotalAmountRaised() {
        return root.node("total_amount_raised").getDouble(0);
    }

    public static void setTotalAmountRaised(double amount) throws SerializationException {
        root.node("total_amount_raised").set(amount);
        try {
            YamlConfigurationLoader.builder().path(configFile.toPath()).build().save(root);
        } catch (SerializationException e) {
            throw new SerializationException("Failed to set total amount raised".getClass(), e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getMainTitleColor() { // Replaces '&' with '§' for Minecraft color codes
        return root.node("main-title-color").getString("&d").replace("&", "§");
    }

    public static String getMainBarColor() {
        return root.node("main-bar-color").getString("WHITE");
    }

    public static String getGoalMessage() {
        return root.node("goal-message").getString("Goal Reached!");
    }

    public static String getGoalTitleColor() {
        return root.node("goal-title-color").getString("&f");
    }

    public static String getGoalBarColor() {
        return root.node("goal-bar-color").getString("BLUE");
    }

    public static ConfigurationNode getDonationsNode() {
        return root.node("donations");
    }

    public static ConfigurationNode getGoalsNode() {
        return root.node("goals");
    }

    public static boolean isRepeatGoalKey(String key) {
        return key.startsWith("repeat-") && key.substring("repeat-".length()).matches("\\d+");
    }


    private static void save() {
        try {
            YamlConfigurationLoader.builder()
                    .path(configFile.toPath())
                    .build()
                    .save(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
