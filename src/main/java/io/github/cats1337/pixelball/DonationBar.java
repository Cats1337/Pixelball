package io.github.cats1337.pixelball;

import com.google.common.net.HttpHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DonationBar {
    private ServerBossBar bar;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DonationBar() {}

    public void updateBar() {
        MinecraftServer server = PixelballUtils.getServer();
        if (server == null) {
            Pixelball.LOGGER.warn("[Pixelball] Server is null, cannot update donation bar");
            return;
        }

        String id = PixelballConfig.getCampaignId();
        String mainTitleColor = PixelballConfig.getMainTitleColor();
        String mainBarColor = PixelballConfig.getMainBarColor();
        String goalMsg = PixelballConfig.getGoalMessage();
        String goalTitleColor = PixelballConfig.getGoalTitleColor();
        String goalBarColor = PixelballConfig.getGoalBarColor();

        try {
            JsonObject jsonObject = requestJson(id);
            JsonObject data = jsonObject.get("data").getAsJsonObject();
            double totalRaised = data.get("amount_raised").getAsJsonObject().get("value").getAsDouble();
            double goalAmount = data.get("goal").getAsJsonObject().get("value").getAsDouble();
            double progress = totalRaised / goalAmount;

            server.execute(() -> {
                if (bar == null) {
                    createBar();
                }
                if (bar != null) {
                    if (progress >= 1.0) {
                        bar.setPercent(1.0f);
                        bar.setColor(BossBar.Color.valueOf(goalBarColor));
                        bar.setName(Text.literal(goalTitleColor + goalMsg));
                        Pixelball.LOGGER.info("[Pixelball] Donation goal reached! Total raised: $" + format(totalRaised));
                    } else {
                        bar.setPercent((float) progress);
                        bar.setColor(BossBar.Color.valueOf(mainBarColor));
                        bar.setName(Text.literal(mainTitleColor + "Raised $" + format(totalRaised) + " of $" + format(goalAmount)));
                    }
                }
                double lastRaised = PixelballConfig.getTotalAmountRaised();
                if (totalRaised > lastRaised) {
                    double diff = totalRaised - lastRaised;
                    try {
                        PixelballConfig.setTotalAmountRaised(totalRaised);
                    } catch (SerializationException e) {
                        throw new RuntimeException(e);
                    }
                    GoalEvents events = new GoalEvents();
                    events.checkDonations(server, diff);
                    events.checkGoals(server, totalRaised);
                }
            });


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createBar() {
        this.bar = new ServerBossBar(
                Text.literal("Donation Bar Initialized").formatted(Formatting.RED),
                ServerBossBar.Color.PINK,
                ServerBossBar.Style.PROGRESS
        );
        this.bar.setVisible(true);

        Pixelball.LOGGER.info("[Pixelball] Scheduling donation bar updater...");

        scheduler.scheduleAtFixedRate(this::updateBar, 0, PixelballConfig.getBarUpdateInterval(), TimeUnit.SECONDS);
    }

    public void addPlayer(ServerPlayerEntity player) {
        if (bar != null) bar.addPlayer(player);
    }

    public ServerBossBar getBossBar() {
        return this.bar;
    }

    public void attemptToCancel() {
        scheduler.shutdownNow();
        if (bar != null) {
            bar.clearPlayers();
        }
    }

    private String format(double number) {
        return NumberFormat.getInstance(Locale.US).format(number);
    }

    static JsonElement requestToken() throws IOException {
        URL url = new URL("https://v5api.tiltify.com/oauth/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setDoOutput(true);

        String client_id = PixelballConfig.getClientId();
        String client_secret = PixelballConfig.getClientSecret();
        String jsonBody = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + client_id + "\",\"client_secret\":\"" + client_secret + "\",\"scope\":\"public\"}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                return JsonParser.parseReader(reader);
            }
        }

        throw new IOException("Failed to get token from Tiltify");
    }

    static JsonObject requestJson(String id) throws IOException {
        URL url = new URL("https://v5api.tiltify.com/api/public/team_campaigns/" + id);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String token = requestToken().getAsJsonObject().get("access_token").getAsString();
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod("GET");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            JsonElement data = JsonParser.parseReader(reader);
            if (data != null && data.isJsonObject()) return data.getAsJsonObject();
        }

        throw new IOException("Invalid response from Tiltify");
    }
}
