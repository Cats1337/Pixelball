package io.github.cats1337.pixelball;

import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.util.Identifier;

import java.text.NumberFormat;
import java.util.Locale;

public final class PixelballPlaceholders {
    private PixelballPlaceholders() {
    }

    public static void register() {
        Placeholders.register(
                Identifier.of(Pixelball.MOD_ID, "total_raised"),
                (ctx, arg) -> PlaceholderResult.value(getTotalRaised())
        );

        Placeholders.register(
                Identifier.of(Pixelball.MOD_ID, "total_raised_raw"),
                (ctx, arg) -> PlaceholderResult.value(getTotalRaisedRaw())
        );

        Pixelball.LOGGER.info("[Pixelball] Registered Placeholder API placeholders.");
    }

    private static String getTotalRaised() {
        double total = PixelballConfig.getTotalAmountRaised()
                + PixelballConfig.getTotalAmountOverride();

        return "$" + format(total);
    }

    private static String getTotalRaisedRaw() {
        double total = PixelballConfig.getTotalAmountRaised()
                + PixelballConfig.getTotalAmountOverride();

        return format(total);
    }

    private static String format(double number) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(number);
    }
}