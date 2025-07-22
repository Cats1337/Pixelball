package io.github.cats1337.pixelball;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class DonationCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("donationbb")
                .then(literal("reload")
                        .executes(ctx -> {
                            Pixelball.INSTANCE.createBossBar();
                            ctx.getSource().sendFeedback(() -> Text.literal("§aReloaded Donation Bossbar"), false);
                            return 1;
                        }))
                .then(literal("raised")
                        .then(literal("set")
                                .then(argument("amount", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> {
                                            double newAmount = DoubleArgumentType.getDouble(ctx, "amount");
                                            try {
                                                PixelballConfig.setTotalAmountRaised(newAmount);
                                            } catch (SerializationException e) {
                                                throw new RuntimeException(e);
                                            }

                                            String id = PixelballConfig.getCampaignId();
                                            String mainTitleColor = PixelballConfig.getMainTitleColor();

                                            try {
                                                JsonObject data = DonationBar.requestJson(id).get("data").getAsJsonObject();
                                                double goal = data.get("goal").getAsJsonObject().get("value").getAsDouble();

                                                if (Pixelball.INSTANCE.getDonationBar() != null) {
                                                    Pixelball.INSTANCE.getDonationBar().getBossBar().setName(
                                                            Text.literal(mainTitleColor + "Raised $" +
                                                                    format(newAmount) + " of $" + format(goal)));
                                                }

                                                ctx.getSource().sendFeedback(() -> Text.literal("§aUpdated amount raised to $" + newAmount), false);
                                            } catch (IOException e) {
                                                ctx.getSource().sendError(Text.literal("§cFailed to fetch goal data."));
                                                e.printStackTrace();
                                            } catch (Exception e) {
                                                ctx.getSource().sendError(Text.literal("§cUnexpected error: " + e.getMessage()));
                                                e.printStackTrace();
                                            }

                                            return 1;
                                        }))))

                .then(literal("simulate")
                        .then(argument("user", StringArgumentType.string())
                                .then(argument("amount", DoubleArgumentType.doubleArg())
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

                                                    ctx.getSource().sendFeedback(() -> Text.literal("§aSimulating donation from §e" + user + " §aof §6$" + amount + " §7with message: §f" + message), false);

                                                    // Update total raised
                                                    double oldTotal = PixelballConfig.getTotalAmountRaised();
                                                    double newTotal = oldTotal + amount;
                                                    try {
                                                        PixelballConfig.setTotalAmountRaised(newTotal);
                                                    } catch (SerializationException e) {
                                                        throw new RuntimeException(e);
                                                    }

                                                    // Simulate the bar update
//                                                    DonationBar.update(server, newTotal);

                                                    // Trigger donation & goal events as if it were real
                                                    GoalEvents goalEvents = new GoalEvents();

                                                    // Use a custom JSON blob for donation parser
                                                    JsonObject fakeDonor = new JsonObject();
                                                    JsonObject amt = new JsonObject();
                                                    amt.addProperty("value", amount);
                                                    fakeDonor.add("amount", amt);
                                                    fakeDonor.addProperty("donor_name", user);
                                                    fakeDonor.addProperty("donor_comment", message);

                                                    goalEvents.checkDonationsWithJson(server, fakeDonor);
                                                    goalEvents.checkGoals(server, newTotal);

                                                    return 1;
                                                })))))

        );
    }

    private static String format(double number) {
        return NumberFormat.getInstance(Locale.US).format(number);
    }
}
