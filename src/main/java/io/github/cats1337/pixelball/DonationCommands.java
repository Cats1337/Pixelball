package io.github.cats1337.pixelball;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class DonationCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("pixelball")
                .executes(ctx -> sendDonateInfo(ctx.getSource()))
                // Help
                .then(literal("help")
                        .executes(ctx -> sendHelp(ctx.getSource())))
                // Reload
                .then(literal("reload")
                        .requires(source -> hasPerm(source, "pixelball.reload"))
                        .executes(ctx -> {
                            try {
                                PixelballConfig.reload();
                                Pixelball.INSTANCE.createBossBar();
                            } catch (IOException e) {
                                ctx.getSource().sendError(Text.literal("§cFailed to reload Pixelball config."));
                                Pixelball.LOGGER.error("[Pixelball] Failed to reload donation bar", e);
                                return 0;
                            }

                            ctx.getSource().sendFeedback(() -> Text.literal("§aReloaded Pixelball donation bar."), false);
                            return 1;
                        }))
                // Action
                .then(literal("action")
                        .requires(source -> hasPerm(source, "pixelball.action"))
                        .then(literal("random_pokeball")
                                .executes(ctx -> runActionCommand(ctx.getSource(), "random_pokeball")))
                        .then(literal("random_stone")
                                .executes(ctx -> runActionCommand(ctx.getSource(), "random_stone")))
                        .then(literal("random_held_item")
                                .executes(ctx -> runActionCommand(ctx.getSource(), "random_held_item")))
                        .then(literal("legendaryspawn")
                                .then(argument("count", DoubleArgumentType.doubleArg(1))
                                        .executes(ctx -> runActionCommand(
                                                ctx.getSource(),
                                                "legendaryspawn " + (int) DoubleArgumentType.getDouble(ctx, "count")
                                        ))))
                        .then(literal("enable_nether")
                                .executes(ctx -> runActionCommand(ctx.getSource(), "enable_nether")))
                        .then(literal("enable_end")
                                .executes(ctx -> runActionCommand(ctx.getSource(), "enable_end"))))
                // Bypass
                .then(literal("bypass")
                        .requires(source -> hasPerm(source, "pixelball.bypass"))
                        .then(literal("nether")
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                            boolean enabled = PixelballBypass.toggleNether(player);

                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                    enabled
                                                            ? "§aEnabled Nether bypass for §e" + player.getName().getString()
                                                            : "§cDisabled Nether bypass for §e" + player.getName().getString()
                                            ), false);

                                            return 1;
                                        })))
                        .then(literal("end")
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                            boolean enabled = PixelballBypass.toggleEnd(player);

                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                    enabled
                                                            ? "§aEnabled End bypass for §e" + player.getName().getString()
                                                            : "§cDisabled End bypass for §e" + player.getName().getString()
                                            ), false);

                                            return 1;
                                        }))))
                // Debug
                .then(literal("debug")
                        .requires(source -> hasPerm(source, "pixelball.debug"))
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                                    boolean enabled = PixelballDebug.toggle(player);

                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            enabled
                                                    ? "§aEnabled Pixelball debug messages for §e" + player.getName().getString()
                                                    : "§cDisabled Pixelball debug messages for §e" + player.getName().getString()
                                    ), false);

                                    player.sendMessage(Text.literal(
                                            enabled
                                                    ? "§aPixelball debug messages enabled."
                                                    : "§cPixelball debug messages disabled."
                                    ), false);

                                    return 1;
                                })))
                // Donate
                .then(literal("donate")
                        .executes(ctx -> sendDonateInfo(ctx.getSource()))

                        .then(literal("raised")
                                .requires(source -> hasPerm(source, "pixelball.donate.raised"))
                                .then(literal("set")
                                        .then(argument("amount", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> {
                                                    double desiredDisplayedTotal = DoubleArgumentType.getDouble(ctx, "amount");
                                                    double trueTotalRaised = PixelballConfig.getTotalAmountRaised();
                                                    double override = desiredDisplayedTotal - trueTotalRaised;

                                                    try {
                                                        PixelballConfig.setTotalAmountOverride(override);
                                                    } catch (SerializationException e) {
                                                        ctx.getSource().sendError(Text.literal("§cFailed to save total amount override."));
                                                        Pixelball.LOGGER.error("[Pixelball] Failed to save total amount override", e);
                                                        return 0;
                                                    }

                                                    updateBossBarTitle(desiredDisplayedTotal);

                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                            "§aSet displayed raised amount to §6$" + format(desiredDisplayedTotal) +
                                                                    "§a. Override is now §6$" + format(override) + "§a."
                                                    ), false);

                                                    return 1;
                                                }))))

                        .then(literal("simulate")
                                .requires(source -> hasPerm(source, "pixelball.donate.simulate"))
                                .then(argument("user", StringArgumentType.string())
                                        .then(argument("amount", DoubleArgumentType.doubleArg(0))
                                                .then(argument("message", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            String user = StringArgumentType.getString(ctx, "user");
                                                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                                            String message = StringArgumentType.getString(ctx, "message");

                                                            MinecraftServer server = PixelballUtils.getServer();
                                                            if (server == null) {
                                                                ctx.getSource().sendError(Text.literal("§cServer not available."));
                                                                return 0;
                                                            }

                                                            double trueTotalRaised = PixelballConfig.getTotalAmountRaised();
                                                            double override = PixelballConfig.getTotalAmountOverride();
                                                            double newDisplayedTotal = trueTotalRaised + override + amount;

                                                            try {
                                                                PixelballConfig.setTotalAmountOverride(newDisplayedTotal - trueTotalRaised);
                                                            } catch (SerializationException e) {
                                                                ctx.getSource().sendError(Text.literal("§cFailed to save simulated donation amount."));
                                                                Pixelball.LOGGER.error("[Pixelball] Failed to save simulated donation override", e);
                                                                return 0;
                                                            }

                                                            JsonObject fakeDonor = getJsonObject(amount, user, message);

                                                            GoalEvents goalEvents = new GoalEvents();
                                                            goalEvents.checkDonationsWithJson(server, fakeDonor);
                                                            goalEvents.checkGoals(server, newDisplayedTotal);

                                                            updateBossBarTitle(newDisplayedTotal);

                                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                                    "§aSimulated donation from §e" + user +
                                                                            " §aof §6$" + format(amount) +
                                                                            " §7with message: §f" + message
                                                            ), false);

                                                            return 1;
                                                        }))))))
        );
    }

    private static int runActionCommand(ServerCommandSource source, String action) {
        MinecraftServer server = PixelballUtils.getServer();

        if (server == null) {
            source.sendError(Text.literal("§cServer not available."));
            return 0;
        }

        new GoalEvents().runAction(server, action);

        source.sendFeedback(() -> Text.literal("§aRan Pixelball action: §e" + action), false);
        return 1;
    }

    private static @NotNull JsonObject getJsonObject(double amount, String user, String message) {
        JsonObject fakeDonor = new JsonObject();
        JsonObject amt = new JsonObject();

        amt.addProperty("value", amount);
        fakeDonor.add("amount", amt);
        fakeDonor.addProperty("donor_name", user);
        fakeDonor.addProperty("donor_comment", message);
        return fakeDonor;
    }

    private static void updateBossBarTitle(double displayedTotal) {
        String id = PixelballConfig.getCampaignId();
        if (id.isBlank()) return;

        try {
            JsonObject data = DonationBar.requestJson(id).get("data").getAsJsonObject();
            double goal = data.get("goal").getAsJsonObject().get("value").getAsDouble();

            if (Pixelball.INSTANCE.getDonationBar() != null && Pixelball.INSTANCE.getDonationBar().getBossBar() != null) {
                Pixelball.INSTANCE.getDonationBar().getBossBar().setName(
                        Text.literal(PixelballConfig.getBossbarTitleColor() +
                                "Raised $" + format(displayedTotal) + " of $" + format(goal))
                );

                float progress = goal <= 0 ? 0 : (float) Math.min(displayedTotal / goal, 1.0);
                Pixelball.INSTANCE.getDonationBar().getBossBar().setPercent(progress);
            }
        } catch (IOException e) {
            Pixelball.LOGGER.warn("[Pixelball] Failed to fetch goal data while updating bossbar title", e);
        } catch (Exception e) {
            Pixelball.LOGGER.warn("[Pixelball] Failed to update bossbar title", e);
        }
    }

    private static String format(double number) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(number);
    }

    private static boolean hasPerm(ServerCommandSource source, String permission) {
        return Permissions.check(source, "pixelball.admin", 2)
                || Permissions.check(source, permission, 2);
    }

    private static int sendDonateInfo(ServerCommandSource source) {
        double total = PixelballConfig.getTotalAmountRaised() + PixelballConfig.getTotalAmountOverride();
        String url = PixelballConfig.getDonateUrl();

        source.sendFeedback(() -> Text.literal(
                "§3§lPixelball Donations\n" +
                        "§bRaised: §e$" + format(total) + "\n" +
                        "§bDonate: §f" + (url.isBlank() ? "Not configured" : url)
        ), false);

        return 1;
    }

    private record HelpEntry(String permission, String... lines) {}
    private static final HelpEntry[] HELP_ENTRIES = {
            new HelpEntry(null,
                    "§b/pixelball §7- §eDisplay donate link and amount raised",
                    "§b/pixelball donate §7- §eDisplay donate link and amount raised",
                    "§b/pixelball help §7- §eDisplay this message"),

            new HelpEntry("pixelball.action",
                    "§b/pixelball action random_pokeball §7- §eTest random pokeball reward",
                    "§b/pixelball action random_stone §7- §eTest random stone reward",
                    "§b/pixelball action random_held_item §7- §eTest random held item reward",
                    "§b/pixelball action legendaryspawn <count> §7- §eTest legendary spawn reward",
                    "§b/pixelball action enable_nether §7- §eTest enable nether reward",
                    "§b/pixelball action enable_end §7- §eTest enable end reward"),

            new HelpEntry("pixelball.bypass",
                    "§b/pixelball bypass nether <player> §7- §eAllow player to bypass nether restriction",
                    "§b/pixelball bypass end <player> §7- §eAllow player to bypass end restriction"),

            new HelpEntry("pixelball.debug",
                    "§b/pixelball debug <player> §7- §eToggle legendary spawn debug messages"),

            new HelpEntry("pixelball.donate.raised",
                    "§b/pixelball donate raised set <amount> §7- §eOverride displayed donation total"),

            new HelpEntry("pixelball.donate.simulate",
                    "§b/pixelball donate simulate <user> <amount> <message> §7- §eSimulate a donation"),

            new HelpEntry("pixelball.reload",
                    "§b/pixelball reload §7- §eReload config and donation bar")
    };

    private static int sendHelp(ServerCommandSource source) {
        StringBuilder help = new StringBuilder("§3§l§nPixelball Commands\n");

        for (HelpEntry entry : HELP_ENTRIES) {
            if (entry.permission() == null || hasPerm(source, entry.permission())) {
                for (String line : entry.lines()) {
                    help.append(line).append('\n');
                }
                help.append('\n');
            }
        }

        source.sendFeedback(() -> Text.literal(help.toString().trim()), false);
        return 1;
    }
}