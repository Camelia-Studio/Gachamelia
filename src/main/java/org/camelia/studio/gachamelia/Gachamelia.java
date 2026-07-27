package org.camelia.studio.gachamelia;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.ApiConfiguration;
import org.camelia.studio.gachamelia.api.ApiTokenProvider;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.GachameliaApiClient;
import org.camelia.studio.gachamelia.api.http.ApiTransport;
import org.camelia.studio.gachamelia.api.http.JavaHttpApiTransport;
import org.camelia.studio.gachamelia.listeners.ReadyListener;
import org.camelia.studio.gachamelia.managers.CommandManager;
import org.camelia.studio.gachamelia.managers.ListenerManager;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.CatalogueMessageService;
import org.camelia.studio.gachamelia.services.CatalogueRefreshScheduler;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.camelia.studio.gachamelia.services.MemberReconciliationService;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

public class Gachamelia {
    private static final Logger logger = LoggerFactory.getLogger(Gachamelia.class);
    private static final Duration API_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration API_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public static void main(String[] args) {
        JDA jda = null;
        BotEmojiScheduler botEmojiScheduler = null;
        GuildEmojiRefreshDebouncer emojiRefreshDebouncer = null;
        CatalogueRefreshScheduler catalogueRefreshScheduler = null;
        GuildRuntimeCoordinator coordinator = null;
        MemberReconciliationService reconciliationService = null;

        try {
            Configuration configuration = Configuration.getInstance();
            RuntimeConfiguration runtimeConfiguration = RuntimeConfiguration.from(configuration);
            ApiConfiguration apiConfiguration = ApiConfiguration.from(configuration);
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(API_CONNECT_TIMEOUT)
                    .build();
            ApiTransport apiTransport = new JavaHttpApiTransport(httpClient, API_REQUEST_TIMEOUT);
            ApiTokenProvider tokenProvider = new ApiTokenProvider(apiConfiguration, apiTransport, Clock.systemUTC());
            GachameliaApiClient apiClient = new GachameliaApiClient(apiConfiguration, tokenProvider, apiTransport);
            GuildCatalogueCache catalogueCache = new GuildCatalogueCache();
            EmojiSnapshotService emojiSnapshotService = new EmojiSnapshotService();
            BotApiService botApiService = new BotApiService(apiClient, emojiSnapshotService);
            botEmojiScheduler = new BotEmojiScheduler(apiClient, emojiSnapshotService);
            CatalogueMessageService catalogueMessageService = new CatalogueMessageService(RandomGenerator.getDefault());
            AtomicReference<MemberReconciliationService> reconciliationRef = new AtomicReference<>();
            coordinator = new GuildRuntimeCoordinator(
                    botApiService,
                    catalogueCache,
                    runtimeConfiguration,
                    guildId -> {
                        MemberReconciliationService service = reconciliationRef.get();
                        if (service != null) {
                            service.cancel(guildId);
                        }
                    }
            );
            reconciliationService = new MemberReconciliationService(
                    botApiService,
                    catalogueCache,
                    runtimeConfiguration,
                    coordinator::recoverGuild
            );
            reconciliationRef.set(reconciliationService);
            coordinator.setReadyHandler(reconciliationService::request);
            emojiRefreshDebouncer = new GuildEmojiRefreshDebouncer(coordinator::refreshGuildEmojis, Duration.ofSeconds(3));
            CommandManager commandManager = new CommandManager(botApiService, coordinator);
            catalogueRefreshScheduler = new CatalogueRefreshScheduler(coordinator, runtimeConfiguration);
            ReadyListener readyListener = new ReadyListener(coordinator, botEmojiScheduler, catalogueRefreshScheduler);

            jda = JDABuilder.createDefault(configuration.getDotenv().get("BOT_TOKEN"))
                    .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .build()
                    .awaitReady();

            new ListenerManager(
                    commandManager,
                    botApiService,
                    catalogueMessageService,
                    emojiRefreshDebouncer,
                    coordinator,
                    runtimeConfiguration
            ).registerListeners(jda);

            readyListener.initialize(jda);

            JDA readyJda = jda;
            BotEmojiScheduler readyBotEmojiScheduler = botEmojiScheduler;
            GuildEmojiRefreshDebouncer readyEmojiRefreshDebouncer = emojiRefreshDebouncer;
            CatalogueRefreshScheduler readyCatalogueRefreshScheduler = catalogueRefreshScheduler;
            GuildRuntimeCoordinator readyCoordinator = coordinator;
            MemberReconciliationService readyReconciliationService = reconciliationService;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(
                    readyCatalogueRefreshScheduler,
                    readyEmojiRefreshDebouncer,
                    readyCoordinator,
                    readyReconciliationService,
                    readyBotEmojiScheduler,
                    readyJda
            )));
        } catch (InterruptedException e) {
            shutdown(catalogueRefreshScheduler, emojiRefreshDebouncer, coordinator, reconciliationService, botEmojiScheduler, jda);
            Thread.currentThread().interrupt();
            logger.error("Le thread a été interrompu : {}", e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException exception) {
            shutdown(catalogueRefreshScheduler, emojiRefreshDebouncer, coordinator, reconciliationService, botEmojiScheduler, jda);
            logger.error("Configuration invalide : {}", exception.getMessage());
            System.exit(1);
        } catch (ApiException exception) {
            shutdown(catalogueRefreshScheduler, emojiRefreshDebouncer, coordinator, reconciliationService, botEmojiScheduler, jda);
            logger.error("Erreur API au démarrage : {} ({})", exception.errorCode(), exception.statusCode());
            System.exit(1);
        } catch (Exception e) {
            shutdown(catalogueRefreshScheduler, emojiRefreshDebouncer, coordinator, reconciliationService, botEmojiScheduler, jda);
            logger.error("Une erreur est survenue lors de l'exécution du bot : {}", e.getMessage());
            System.exit(1);
        }
    }

    static void shutdown(
            CatalogueRefreshScheduler catalogueRefreshScheduler,
            GuildEmojiRefreshDebouncer emojiRefreshDebouncer,
            GuildRuntimeCoordinator coordinator,
            MemberReconciliationService reconciliationService,
            BotEmojiScheduler botEmojiScheduler,
            JDA jda
    ) {
        closeQuietly("catalogue refresh scheduler", catalogueRefreshScheduler == null ? null : catalogueRefreshScheduler::close);
        closeQuietly("guild emoji refresh debouncer", emojiRefreshDebouncer == null ? null : emojiRefreshDebouncer::close);
        closeQuietly("guild runtime coordinator", coordinator == null ? null : coordinator::close);
        closeQuietly("member reconciliation service", reconciliationService == null ? null : reconciliationService::close);
        closeQuietly("bot emoji scheduler", botEmojiScheduler == null ? null : botEmojiScheduler::close);
        closeQuietly("JDA", jda == null ? null : jda::shutdown);
    }

    private static void closeQuietly(String component, Runnable closeAction) {
        if (closeAction == null) {
            return;
        }
        try {
            closeAction.run();
        } catch (Throwable exception) {
            logger.warn("Impossible d'arrêter {}", component, exception);
        }
    }
}
