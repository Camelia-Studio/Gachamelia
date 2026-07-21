package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MemberReconciliationService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MemberReconciliationService.class);

    private final BotApiService botApiService;
    private final GuildCatalogueCache catalogueCache;
    private final boolean syncBotMembers;
    private final Consumer<Guild> recoveryRequest;
    private final ExecutorService executor;
    private final Map<String, ReconciliationState> states = new ConcurrentHashMap<>();

    public MemberReconciliationService(
            BotApiService botApiService,
            GuildCatalogueCache catalogueCache,
            RuntimeConfiguration configuration,
            Consumer<Guild> recoveryRequest
    ) {
        this(
                botApiService,
                catalogueCache,
                configuration.syncBotMembers(),
                recoveryRequest,
                Executors.newFixedThreadPool(configuration.memberSyncConcurrency())
        );
    }

    MemberReconciliationService(
            BotApiService botApiService,
            GuildCatalogueCache catalogueCache,
            boolean syncBotMembers,
            Consumer<Guild> recoveryRequest,
            ExecutorService executor
    ) {
        this.botApiService = botApiService;
        this.catalogueCache = catalogueCache;
        this.syncBotMembers = syncBotMembers;
        this.recoveryRequest = recoveryRequest;
        this.executor = executor;
    }

    public void request(Guild guild) {
        String guildId = guild.getId();
        while (true) {
            ReconciliationState state = states.computeIfAbsent(guildId, ignored -> new ReconciliationState());
            boolean startRun = false;
            synchronized (state) {
                if (!isCurrent(guildId, state)) {
                    continue;
                }
                if (state.running) {
                    state.trailingRequested = true;
                } else {
                    state.running = true;
                    startRun = true;
                }
            }
            if (startRun) {
                startRun(guild, state);
            }
            return;
        }
    }

    public void cancel(String guildId) {
        ReconciliationState state = states.remove(guildId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.cancelled = true;
            state.trailingRequested = false;
            state.running = false;
        }
    }

    @Override
    public void close() {
        states.forEach((guildId, state) -> cancel(guildId));
        executor.shutdownNow();
    }

    private void startRun(Guild guild, ReconciliationState state) {
        if (!isCurrent(guild.getId(), state)) {
            return;
        }
        try {
            guild.loadMembers()
                    .onSuccess(members -> submitMembers(guild, state, members))
                    .onError(error -> {
                        logger.warn("Impossible de charger les membres du serveur {}", guild.getId(), error);
                        finishRun(guild, state);
                    });
        } catch (RuntimeException exception) {
            logger.warn("Impossible de démarrer le chargement des membres du serveur {}", guild.getId(), exception);
            finishRun(guild, state);
        }
    }

    private void submitMembers(Guild guild, ReconciliationState state, List<Member> members) {
        String guildId = guild.getId();
        if (!isCurrent(guildId, state) || catalogueCache.findReady(guildId).isEmpty()) {
            finishRun(guild, state);
            return;
        }

        List<Member> eligibleMembers = members.stream()
                .filter(member -> syncBotMembers || !member.getUser().isBot())
                .toList();
        if (eligibleMembers.isEmpty()) {
            finishRun(guild, state);
            return;
        }

        ReconciliationRun run = new ReconciliationRun(eligibleMembers.size());
        for (Member member : eligibleMembers) {
            try {
                executor.submit(() -> reconcileMember(guild, state, run, member));
            } catch (RuntimeException exception) {
                logger.warn("Impossible de planifier la réconciliation du membre {}", member.getId(), exception);
                finishMember(guild, state, run);
            }
        }
    }

    private void reconcileMember(Guild guild, ReconciliationState state, ReconciliationRun run, Member member) {
        try {
            String guildId = guild.getId();
            if (!isCurrent(guildId, state) || run.abortRequested.get() || catalogueCache.findReady(guildId).isEmpty()) {
                return;
            }

            CatalogueEnvelope catalogue = catalogueCache.findReady(guildId).orElseThrow();
            UserEnvelope envelope = isStaffMember(guild, member, catalogue)
                    ? botApiService.ensureStaffUser(guildId, member.getId())
                    : botApiService.ensureUser(guildId, member.getId());
            addRankRole(guild, member, envelope);
        } catch (ApiException exception) {
            if (isRecoverable(exception) && run.recoveryRequested.compareAndSet(false, true)) {
                run.abortRequested.set(true);
                try {
                    recoveryRequest.accept(guild);
                } catch (RuntimeException recoveryException) {
                    logger.warn("Impossible de récupérer le serveur {}", guild.getId(), recoveryException);
                }
            } else {
                logger.warn("Impossible de réconcilier le membre {} du serveur {}", member.getId(), guild.getId(), exception);
            }
        } catch (RuntimeException exception) {
            logger.warn("Impossible de réconcilier le membre {} du serveur {}", member.getId(), guild.getId(), exception);
        } finally {
            finishMember(guild, state, run);
        }
    }

    private boolean isStaffMember(Guild guild, Member member, CatalogueEnvelope catalogue) {
        ApiDiscordServer server = catalogue.server();
        String staffRoleId = server != null && server.settings() != null
                ? server.settings().staffRoleId()
                : null;
        Role staffRole = staffRoleId != null ? guild.getRoleById(staffRoleId) : null;
        return staffRole != null && member.getRoles().contains(staffRole);
    }

    private void addRankRole(Guild guild, Member member, UserEnvelope envelope) {
        ApiUser user = envelope != null ? envelope.user() : null;
        if (user == null || user.rank() == null) {
            throw new ApiException(
                    502,
                    "api_user_rank_missing",
                    "Missing user rank for guild %s and member %s".formatted(guild.getId(), member.getId())
            );
        }
        String rankRoleId = user.rank().discordId();
        Role rankRole = rankRoleId != null ? guild.getRoleById(rankRoleId) : null;
        if (rankRole != null) {
            guild.addRoleToMember(member, rankRole).queue();
        }
    }

    private void finishMember(Guild guild, ReconciliationState state, ReconciliationRun run) {
        if (run.remainingMembers.decrementAndGet() == 0) {
            finishRun(guild, state);
        }
    }

    private void finishRun(Guild guild, ReconciliationState state) {
        String guildId = guild.getId();
        boolean startTrailingRun = false;
        synchronized (state) {
            if (!isCurrent(guildId, state) || !state.running) {
                return;
            }
            if (state.trailingRequested && catalogueCache.findReady(guildId).isPresent()) {
                state.trailingRequested = false;
                startTrailingRun = true;
            } else {
                state.running = false;
                states.remove(guildId, state);
            }
        }
        if (startTrailingRun) {
            startRun(guild, state);
        }
    }

    private boolean isCurrent(String guildId, ReconciliationState state) {
        return !state.cancelled && states.get(guildId) == state;
    }

    private boolean isRecoverable(ApiException exception) {
        return exception.statusCode() == 409 && "server_inactive".equals(exception.errorCode())
                || exception.statusCode() == 404 && "server_not_found".equals(exception.errorCode());
    }

    private static final class ReconciliationState {
        private boolean running;
        private boolean trailingRequested;
        private volatile boolean cancelled;
    }

    private static final class ReconciliationRun {
        private final AtomicInteger remainingMembers;
        private final AtomicBoolean abortRequested = new AtomicBoolean();
        private final AtomicBoolean recoveryRequested = new AtomicBoolean();

        private ReconciliationRun(int memberCount) {
            remainingMembers = new AtomicInteger(memberCount);
        }
    }
}
