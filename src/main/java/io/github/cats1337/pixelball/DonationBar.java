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
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DonationBar {
    private static final String TILTIFY_BASE_URL = "https://v5api.tiltify.com";
    private static final Object TOKEN_LOCK = new Object();

    private static String cachedToken = null;
    private static long cachedTokenExpiresAtMillis = 0;

    private ServerBossBar bar;
    private ScheduledExecutorService scheduler;
    private boolean started = false;

    public void start() {
        if (started) return;
        started = true;

        createBar();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Pixelball-Tiltify-Updater");
            thread.setDaemon(true);
            return thread;
        });

        Pixelball.LOGGER.info("[Pixelball] Scheduling donation bar updater every {} seconds", PixelballConfig.getBarUpdateInterval());
        scheduler.scheduleWithFixedDelay(this::updateBar, 0, PixelballConfig.getBarUpdateInterval(), TimeUnit.SECONDS);
    }

    private void updateBar() {
        MinecraftServer server = PixelballUtils.getServer();
        if (server == null) {
            Pixelball.LOGGER.warn("[Pixelball] Server is null, cannot update donation bar");
            return;
        }

        String campaignId = PixelballConfig.getCampaignId();
        if (campaignId.isBlank()) {
            Pixelball.LOGGER.warn("[Pixelball] No Tiltify campaign-id configured");
            return;
        }

        try {
            JsonObject campaign = requestJson(campaignId);
            JsonObject data = campaign.getAsJsonObject("data");
            if (data == null) throw new IOException("Tiltify response did not include campaign data");

            double totalRaised = getMoneyValue(data, "amount_raised");
            double goalAmount = getMoneyValue(data, "goal");

            server.execute(() -> applyCampaignUpdate(server, totalRaised, goalAmount));
        } catch (Exception e) {
            Pixelball.LOGGER.warn("[Pixelball] Failed to update Tiltify donation bar: {}", e.getMessage());
        }
    }

    private static BossBar.Style parseBarStyle(String value) {
        try {
            return BossBar.Style.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            Pixelball.LOGGER.warn("[Pixelball] Invalid bossbar style '{}', using {}", value, "PROGRESS");
            return BossBar.Style.PROGRESS;
        }
    }

    private void applyCampaignUpdate(MinecraftServer server, double trueTotalRaised, double goalAmount) {
        if (bar == null) createBar();
        if (bar == null) return;

        double displayTotalRaised = trueTotalRaised + PixelballConfig.getTotalAmountOverride();
        double progress = goalAmount <= 0 ? 0 : Math.min(displayTotalRaised / goalAmount, 1.0);
        bar.setStyle(parseBarStyle(PixelballConfig.getBossbarStyle()));

        if (progress >= 1.0) {
            bar.setPercent(1.0f);
            bar.setColor(parseBarColor(PixelballConfig.getGoalBarColor(), BossBar.Color.BLUE));
            bar.setName(Text.literal(PixelballConfig.getGoalTitleColor() + PixelballConfig.getGoalMessage()));
        } else {
            bar.setPercent((float) progress);
            bar.setColor(parseBarColor(PixelballConfig.getBossbarColor(), BossBar.Color.WHITE));
            bar.setName(Text.literal(PixelballConfig.getBossbarTitleColor() + "Raised $" + format(displayTotalRaised) + " of $" + format(goalAmount)));
        }

        double lastTrueRaised = PixelballConfig.getTotalAmountRaised();

        try {
            PixelballConfig.setTotalAmountRaised(trueTotalRaised);
        } catch (SerializationException e) {
            Pixelball.LOGGER.error("[Pixelball] Failed to save total amount raised", e);
            return;
        }

        if (trueTotalRaised <= lastTrueRaised) return;

        double increase = trueTotalRaised - lastTrueRaised;

        GoalEvents events = new GoalEvents();
        events.checkDonations(server, increase);
        events.checkGoals(server, displayTotalRaised);
    }

    public void createBar() {
        if (bar != null) return;

        this.bar = new ServerBossBar(
                Text.literal("Donation Bar Initialized").formatted(Formatting.RED),
                ServerBossBar.Color.PINK,
                ServerBossBar.Style.PROGRESS
        );
        this.bar.setVisible(true);
    }

    public void addPlayer(ServerPlayerEntity player) {
        if (bar != null) bar.addPlayer(player);
    }

    public ServerBossBar getBossBar() {
        return this.bar;
    }

    public void attemptToCancel() {
        started = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (bar != null) {
            bar.clearPlayers();
            bar = null;
        }
    }

    private static double getMoneyValue(JsonObject data, String key) throws IOException {
        JsonObject money = data.getAsJsonObject(key);
        if (money == null || !money.has("value")) {
            throw new IOException("Tiltify response missing money field: " + key);
        }
        return money.get("value").getAsDouble();
    }

    private static BossBar.Color parseBarColor(String value, BossBar.Color fallback) {
        try {
            return BossBar.Color.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            Pixelball.LOGGER.warn("[Pixelball] Invalid bossbar color '{}', using {}", value, fallback);
            return fallback;
        }
    }

    private static String format(double number) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(number);
    }

    static JsonElement requestToken() throws IOException {
        synchronized (TOKEN_LOCK) {
            long now = System.currentTimeMillis();
            if (cachedToken != null && now < cachedTokenExpiresAtMillis) {
                JsonObject response = new JsonObject();
                response.addProperty("access_token", cachedToken);
                return response;
            }

            String clientId = PixelballConfig.getClientId();
            String clientSecret = PixelballConfig.getClientSecret();
            if (clientId.isBlank() || clientSecret.isBlank()) {
                throw new IOException("Tiltify client-id or client-secret is missing");
            }

            URL url = URI.create(TILTIFY_BASE_URL + "/oauth/token").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Pixelball/" + Pixelball.MOD_VERSION);
            conn.setDoOutput(true);

            String jsonBody = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + escapeJson(clientId) + "\",\"client_secret\":\"" + escapeJson(clientSecret) + "\",\"scope\":\"public\"}";

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Failed to get Tiltify token. HTTP " + status + ": " + readError(conn));
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                JsonObject response = JsonParser.parseReader(reader).getAsJsonObject();
                cachedToken = response.get("access_token").getAsString();
                int expiresIn = response.has("expires_in") ? response.get("expires_in").getAsInt() : 7200;
                cachedTokenExpiresAtMillis = Instant.now().plusSeconds(Math.max(60, expiresIn - 120L)).toEpochMilli();
                return response;
            }
        }
    }

    static JsonObject requestJson(String id) throws IOException {
        return requestApiJson("/api/public/team_campaigns/" + id);
    }

    static JsonObject requestDonorsJson(String id) throws IOException {
        return requestApiJson("/api/public/team_campaigns/" + id + "/donations?limit=10");
    }

    private static JsonObject requestApiJson(String path) throws IOException {
        URL url = URI.create(TILTIFY_BASE_URL + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        String token = requestToken().getAsJsonObject().get("access_token").getAsString();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "Pixelball/" + Pixelball.MOD_VERSION);
        conn.setRequestMethod("GET");

        int status = conn.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("Tiltify API request failed. HTTP " + status + ": " + readError(conn));
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            JsonElement data = JsonParser.parseReader(reader);
            if (data != null && data.isJsonObject()) return data.getAsJsonObject();
        }

        throw new IOException("Invalid response from Tiltify");
    }

    private static String readError(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
            return builder.toString();
        } catch (Exception ignored) {
            return "No error body";
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
