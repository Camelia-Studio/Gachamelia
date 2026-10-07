package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.services.XpAwardService;

public final class MessageXpListener extends ListenerAdapter {
    private final XpAwardService awarder;

    public MessageXpListener(XpAwardService awarder) {
        this.awarder = awarder;
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot() || !awarder.configured(event.getGuild().getId())) {
            return;
        }
        var guild = event.getGuild();
        var member = event.getMember();
        if (member != null) {
            Thread.startVirtualThread(() -> awarder.award(guild, member, "message"));
        }
    }
}
