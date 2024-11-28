package org.camelia.studio.gachamelia.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;

import java.time.Instant;

public class EmbedUtils {
    public static EmbedBuilder createDefaultEmbed(JDA jda) {
        return new EmbedBuilder()
                .setTimestamp(Instant.now())
                .setFooter(
                        "Gachamélia v%s « %s »".formatted(
                                Configuration.getInstance().getDotenv().get("APP_VERSION", "0.0.1"),
                                Configuration.getInstance().getDotenv().get("APP_DESCRIPTION", "J'ai posé un pied à terre.")
                        ),
                        jda.getSelfUser().getAvatarUrl()
                )
                .setColor(0x2F3136)
                ;
    }
}
