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
import org.camelia.studio.gachamelia.utils.Configuration;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
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

        try {
            Configuration.getInstance();
            ApiConfiguration apiConfiguration = ApiConfiguration.from(Configuration.getInstance());
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(API_CONNECT_TIMEOUT)
                    .build();
            ApiTransport apiTransport = new JavaHttpApiTransport(httpClient, API_REQUEST_TIMEOUT);
            ApiTokenProvider tokenProvider = new ApiTokenProvider(apiConfiguration, apiTransport, Clock.systemUTC());
            GachameliaApiClient apiClient = new GachameliaApiClient(apiConfiguration, tokenProvider, apiTransport);
            GuildCatalogueCache catalogueCache = new GuildCatalogueCache();
            EmojiSnapshotService emojiSnapshotService = new EmojiSnapshotService();
            RuntimeConfiguration runtimeConfiguration = RuntimeConfiguration.from(Configuration.getInstance());
            BotApiService botApiService = new BotApiService(apiClient, emojiSnapshotService);
            botEmojiScheduler = new BotEmojiScheduler(apiClient, emojiSnapshotService);
            emojiRefreshDebouncer = new GuildEmojiRefreshDebouncer(apiClient, emojiSnapshotService, Duration.ofSeconds(3));
            CatalogueMessageService catalogueMessageService = new CatalogueMessageService(RandomGenerator.getDefault());
            coordinator = new GuildRuntimeCoordinator(botApiService, catalogueCache, runtimeConfiguration, ignored -> { });
            CommandManager commandManager = new CommandManager(botApiService, coordinator);
            catalogueRefreshScheduler = new CatalogueRefreshScheduler(coordinator, runtimeConfiguration);
            ReadyListener readyListener = new ReadyListener(coordinator, botEmojiScheduler, catalogueRefreshScheduler);

            jda = JDABuilder.createDefault(Configuration.getInstance().getDotenv().get("BOT_TOKEN"))
                    .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .build()
                    .awaitReady();

            new ListenerManager(
                    commandManager,
                    botApiService,
                    catalogueMessageService,
                    emojiRefreshDebouncer,
                    coordinator
            ).registerListeners(jda);

            readyListener.initialize(jda);

            JDA readyJda = jda;
            BotEmojiScheduler readyBotEmojiScheduler = botEmojiScheduler;
            GuildEmojiRefreshDebouncer readyEmojiRefreshDebouncer = emojiRefreshDebouncer;
            CatalogueRefreshScheduler readyCatalogueRefreshScheduler = catalogueRefreshScheduler;
            GuildRuntimeCoordinator readyCoordinator = coordinator;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(
                    readyCatalogueRefreshScheduler,
                    readyBotEmojiScheduler,
                    readyEmojiRefreshDebouncer,
                    readyCoordinator,
                    readyJda
            )));
        } catch (InterruptedException e) {
            shutdown(catalogueRefreshScheduler, botEmojiScheduler, emojiRefreshDebouncer, coordinator, jda);
            Thread.currentThread().interrupt();
            logger.error("Le thread a été interrompu : {}", e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException exception) {
            shutdown(catalogueRefreshScheduler, botEmojiScheduler, emojiRefreshDebouncer, coordinator, jda);
            logger.error("Configuration invalide : {}", exception.getMessage());
            System.exit(1);
        } catch (ApiException exception) {
            shutdown(catalogueRefreshScheduler, botEmojiScheduler, emojiRefreshDebouncer, coordinator, jda);
            logger.error("Erreur API au démarrage : {} ({})", exception.errorCode(), exception.statusCode());
            System.exit(1);
        } catch (Exception e) {
            shutdown(catalogueRefreshScheduler, botEmojiScheduler, emojiRefreshDebouncer, coordinator, jda);
            logger.error("Une erreur est survenue lors de l'exécution du bot : {}", e.getMessage());
            System.exit(1);
        }
    }

    static void shutdown(BotEmojiScheduler botEmojiScheduler, GuildEmojiRefreshDebouncer emojiRefreshDebouncer, JDA jda) {
        if (botEmojiScheduler != null) {
            botEmojiScheduler.close();
        }
        if (emojiRefreshDebouncer != null) {
            emojiRefreshDebouncer.close();
        }
        if (jda != null) {
            jda.shutdown();
        }
    }

    static void shutdown(
            CatalogueRefreshScheduler catalogueRefreshScheduler,
            BotEmojiScheduler botEmojiScheduler,
            GuildEmojiRefreshDebouncer emojiRefreshDebouncer,
            GuildRuntimeCoordinator coordinator,
            JDA jda
    ) {
        if (catalogueRefreshScheduler != null) {
            catalogueRefreshScheduler.close();
        }
        if (emojiRefreshDebouncer != null) {
            emojiRefreshDebouncer.close();
        }
        if (coordinator != null) {
            coordinator.close();
        }
        if (botEmojiScheduler != null) {
            botEmojiScheduler.close();
        }
        if (jda != null) {
            jda.shutdown();
        }
    }
}
