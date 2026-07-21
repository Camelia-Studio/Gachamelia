package org.camelia.studio.gachamelia.services;

public class GuildNotReadyException extends RuntimeException {
    private final String guildId;

    public GuildNotReadyException(String guildId) {
        super("Guild is not ready: " + guildId);
        this.guildId = guildId;
    }

    public String guildId() {
        return guildId;
    }
}
