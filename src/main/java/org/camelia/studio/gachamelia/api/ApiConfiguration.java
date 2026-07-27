package org.camelia.studio.gachamelia.api;

import org.camelia.studio.gachamelia.utils.Configuration;

import java.net.URI;

public record ApiConfiguration(String baseUrl, String clientId, String clientSecret) {
    public ApiConfiguration {
        baseUrl = normalizeRequired("API_BASE_URL", baseUrl);
        clientId = require("API_CLIENT_ID", clientId);
        clientSecret = require("API_CLIENT_SECRET", clientSecret);
    }

    public static ApiConfiguration from(Configuration configuration) {
        return new ApiConfiguration(
                configuration.getRequired("API_BASE_URL"),
                configuration.getRequired("API_CLIENT_ID"),
                configuration.getRequired("API_CLIENT_SECRET")
        );
    }

    public URI apiUri(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(baseUrl + "/api" + normalizedPath);
    }

    private static String normalizeRequired(String key, String value) {
        String required = require(key, value);
        while (required.endsWith("/")) {
            required = required.substring(0, required.length() - 1);
        }
        return required;
    }

    private static String require(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }
}
