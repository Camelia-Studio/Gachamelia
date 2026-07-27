package org.camelia.studio.gachamelia.utils;

import java.time.Duration;
import java.util.Locale;

public record RuntimeConfiguration(
        Duration catalogueRefreshInterval,
        int memberSyncConcurrency,
        boolean syncBotMembers
) {
    private static final int DEFAULT_REFRESH_MINUTES = 5;
    private static final int DEFAULT_MEMBER_CONCURRENCY = 4;

    public static RuntimeConfiguration from(Configuration configuration) {
        return parse(
                configuration.getDotenv().get("CATALOGUE_REFRESH_INTERVAL_MINUTES"),
                configuration.getDotenv().get("MEMBER_SYNC_CONCURRENCY"),
                configuration.getDotenv().get("SYNC_BOT_MEMBERS")
        );
    }

    static RuntimeConfiguration parse(String refreshMinutes, String concurrency, String syncBots) {
        int parsedRefresh = positiveInt(
                "CATALOGUE_REFRESH_INTERVAL_MINUTES",
                refreshMinutes,
                DEFAULT_REFRESH_MINUTES
        );
        int parsedConcurrency = positiveInt(
                "MEMBER_SYNC_CONCURRENCY",
                concurrency,
                DEFAULT_MEMBER_CONCURRENCY
        );
        return new RuntimeConfiguration(
                Duration.ofMinutes(parsedRefresh),
                parsedConcurrency,
                strictBoolean("SYNC_BOT_MEMBERS", syncBots, false)
        );
    }

    private static int positiveInt(String key, String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException(key + " must be strictly positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a strictly positive integer", exception);
        }
    }

    private static boolean strictBoolean(String key, String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
    }
}
