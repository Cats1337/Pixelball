package io.github.cats1337.pixelball;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PixelballConfig {
    private static CommentedConfigurationNode root;
    private static File configFile;

    public static void load() throws IOException {
        File configDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "pixelball");

        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new IOException("Failed to create config directory: " + configDir.getAbsolutePath());
        }

        configFile = new File(configDir, "config.yml");

        if (!configFile.exists()) {
            try (InputStream in = Pixelball.class.getResourceAsStream("/config.yml")) {
                if (in == null) {
                    throw new IOException("Default config.yml is missing from resources.");
                }

                Files.copy(in, configFile.toPath());

                Pixelball.LOGGER.info("[Pixelball] Created default config at {}", configFile.getAbsolutePath());
            }
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configFile.toPath())
                .build();

        root = loader.load();
    }

    public static void reload() throws IOException {
        load();
    }

    public static List<String> getStones() {
        try {
            return root.node("stones").getList(String.class, List.of());
        } catch (SerializationException e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to load stones from config", e);
            return List.of();
        }
    }

    public static List<String> getHeldItems() {
        try {
            return root.node("held-items").getList(String.class, List.of());
        } catch (SerializationException e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to load held items from config", e);
            return List.of();
        }
    }

    public static List<String> getPokeballs() {
        try {
            return root.node("pokeballs").getList(String.class, List.of());
        } catch (SerializationException e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to load pokeballs from config", e);
            return List.of();
        }
    }

    public static Map<String, Integer> getLegendaries() {
        Map<String, Integer> legendaries = new LinkedHashMap<>();

        root.node("legendaries").childrenMap().forEach((key, value) ->
                legendaries.put(key.toString(), value.getInt())
        );

        return legendaries;
    }

    public static String getClientId() {
        return root.node("tiltify", "client-id").getString("");
    }

    public static String getClientSecret() {
        return root.node("tiltify", "client-secret").getString("");
    }

    public static String getCampaignId() {
        return root.node("tiltify", "campaign-id").getString("");
    }

    public static String getDonateUrl() {
        return root.node("tiltify", "donation-url").getString("");
    }

    public static double getTotalAmountRaised() {
        return root.node("donation", "total-raised").getDouble(0);
    }

    public static void setTotalAmountRaised(double amount) throws SerializationException {
        root.node("donation", "total-raised").set(amount);
        save();
    }

    public static double getTotalAmountOverride() {
        return root.node("donation", "total-override").getDouble(0);
    }

    public static void setTotalAmountOverride(double amount) throws SerializationException {
        root.node("donation", "total-override").set(amount);
        save();
    }

    public static String getBossbarTitleColor() {
        return root.node("bossbar", "title-color").getString("&3").replace("&", "§");
    }

    public static String getBossbarColor() {
        return root.node("bossbar", "color").getString("BLUE");
    }

    public static String getBossbarStyle() {
        return root.node("bossbar", "style").getString("PROGRESS");
    }

    public static int getBarUpdateInterval() {
        int interval = root.node("bossbar", "update-interval-seconds").getInt(30);
        return Math.max(interval, 5);
    }

    public static String getGoalMessage() {
        return root.node("goal", "message").getString("Goal Reached!");
    }

    public static String getGoalTitleColor() {
        return root.node("goal", "title-color").getString("&f").replace("&", "§");
    }

    public static String getGoalBarColor() {
        return root.node("goal", "color").getString("BLUE");
    }

    public static boolean isDimensionDisabled(String dimension) {
        return !root.node("dimensions", dimension).getBoolean(false);
    }

    public static void setDimensionEnabled(String dimension, boolean enabled) throws SerializationException {
        root.node("dimensions", dimension).set(enabled);
        save();
    }

    public static CommentedConfigurationNode getDonationsNode() {
        return root.node("donations");
    }

    public static CommentedConfigurationNode getGoalsNode() {
        return root.node("goals");
    }

    public static Map<Double, DonationData> getDonations() {
        Map<Double, DonationData> donations = new LinkedHashMap<>();
        CommentedConfigurationNode node = getDonationsNode();

        node.childrenMap().forEach((key, value) -> {
            try {
                double amount = Double.parseDouble(key.toString());
                String action = value.node("action").getString();
                String title = value.node("title").getString();

                if (action != null && title != null) {
                    donations.put(amount, new DonationData(action, title));
                }
            } catch (NumberFormatException ignored) {
            }
        });

        return donations;
    }

    public static Map<Double, GoalData> getGoals() {
        Map<Double, GoalData> goals = new LinkedHashMap<>();

        CommentedConfigurationNode goalsNode = root.node("goals");
        goalsNode.childrenMap().forEach((key, value) -> {
            try {
                double amount = Double.parseDouble(key.toString());
                String action = value.node("action").getString();
                String title = value.node("title").getString();
                boolean reached = value.node("reached").getBoolean(false);

                if (action != null && title != null) {
                    goals.put(amount, new GoalData(action, title, reached, 0, 0));
                }
            } catch (NumberFormatException ignored) {
            }
        });

        CommentedConfigurationNode repeatNode = root.node("repeat");
        repeatNode.childrenMap().forEach((key, value) -> {
            try {
                int interval = Integer.parseInt(key.toString());
                String action = value.node("action").getString();
                String title = value.node("title").getString();
                double lastReached = value.node("last_reached").getDouble(0);

                if (action != null && title != null && interval > 0) {
                    goals.put((double) -interval, new GoalData(action, title, false, interval, lastReached));
                }
            } catch (NumberFormatException ignored) {
            }
        });

        return goals;
    }

    public static void setGoalReached(double amount, boolean reached) {
        String key = String.valueOf((int) amount);
        if (!getGoalsNode().childrenMap().containsKey(key)) return;

        try {
            getGoalsNode().node(key, "reached").set(reached);
            save();
        } catch (SerializationException e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to set goal reached state", e);
        }
    }

    public static void setRepeatGoalLastReached(int interval, double amount) {
        try {
            root.node("repeat", String.valueOf(interval), "last_reached").set(amount);
            save();
        } catch (SerializationException e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to set repeat goal last reached amount", e);
        }
    }

    private static void save() {
        try {
            YamlConfigurationLoader.builder()
                    .path(configFile.toPath())
                    .nodeStyle(NodeStyle.BLOCK)
                    .build()
                    .save(root);
        } catch (IOException e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to save config", e);
        }
    }

    public record DonationData(String action, String title) {
    }

    public static class GoalData {
        private final String action;
        private final String title;
        private final boolean reached;
        private final int interval;
        private final double lastReached;

        public GoalData(String action, String title, boolean reached, int interval, double lastReached) {
            this.action = action;
            this.title = title;
            this.reached = reached;
            this.interval = interval;
            this.lastReached = lastReached;
        }

        public String getAction() {
            return action;
        }

        public String getTitle() {
            return title;
        }

        public boolean isReached() {
            return reached;
        }

        public double getLastReached() { return lastReached; }

        @Override
        public String toString() {
            return "GoalData{title='%s', reached=%s, interval=%d, lastReached=%.2f}"
                    .formatted(title, reached, interval, lastReached);
        }
    }
}