package org.camelia.studio.gachamelia.utils;

import java.util.Map;

public class MessageUtils {

    public static String insertPlaceholders(String message, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return message;
    }
}
