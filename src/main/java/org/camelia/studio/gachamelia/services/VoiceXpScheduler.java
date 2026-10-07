package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class VoiceXpScheduler {
    private static final Logger logger = LoggerFactory.getLogger(VoiceXpScheduler.class);
    private final XpAwardService awarder;
    private final Map<String, Instant> eligibleSince = new ConcurrentHashMap<>();

    public VoiceXpScheduler(XpAwardService awarder) {
        this.awarder = awarder;
    }

    public void start(JDA jda) {
        var executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "gachamelia-voice-xp");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(() -> scan(jda), 30, 30, TimeUnit.SECONDS);
    }

    private void scan(JDA jda) {
        try {
            Instant now = Instant.now();
            Set<String> present = new HashSet<>();
            for (Guild guild : jda.getGuilds()) {
                int voiceIntervalMinutes = awarder.voiceIntervalMinutes(guild.getId());
                if (voiceIntervalMinutes <= 0) {
                    continue;
                }
                Duration period = Duration.ofMinutes(voiceIntervalMinutes);
                for (Member member : guild.getMembers()) {
                    if (member.getUser().isBot() || member.getVoiceState() == null || member.getVoiceState().getChannel() == null) {
                        continue;
                    }
                    String key = guild.getId() + ":" + member.getId();
                    present.add(key);
                    Instant since = eligibleSince.putIfAbsent(key, now);
                    if (since != null && !now.isBefore(since.plus(period))) {
                        eligibleSince.put(key, now);
                        Thread.startVirtualThread(() -> {
                            if (!awarder.award(guild, member, "voice")) {
                                eligibleSince.replace(key, now, since);
                            }
                        });
                    }
                }
            }
            eligibleSince.keySet().retainAll(present);
        } catch (RuntimeException exception) {
            logger.warn("Impossible de calculer l'XP vocal", exception);
        }
    }
}
