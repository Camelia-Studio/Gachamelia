package org.camelia.studio.gachamelia.utils;

import io.github.cdimascio.dotenv.Dotenv;

public class Configuration {
    private static Configuration instance;
    private final Dotenv dotenv;

    public Configuration() {
        this.dotenv = Dotenv.configure().ignoreIfMalformed().ignoreIfMissing().load();
    }

    public static Configuration getInstance() {
        if (instance == null) {
            instance = new Configuration();
        }
        return instance;
    }

    public Dotenv getDotenv() {
        return dotenv;
    }

    public String getRequired(String key) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }
}
