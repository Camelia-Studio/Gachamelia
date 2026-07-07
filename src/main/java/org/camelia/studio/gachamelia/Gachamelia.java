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
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;
import org.camelia.studio.gachamelia.utils.Configuration;
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
            BotApiService botApiService = new BotApiService(apiClient, catalogueCache, emojiSnapshotService);
            botEmojiScheduler = new BotEmojiScheduler(apiClient, emojiSnapshotService);
            emojiRefreshDebouncer = new GuildEmojiRefreshDebouncer(apiClient, emojiSnapshotService, Duration.ofSeconds(3));
            CatalogueMessageService catalogueMessageService = new CatalogueMessageService(RandomGenerator.getDefault());
            CommandManager commandManager = new CommandManager(botApiService, catalogueCache);
            ReadyListener readyListener = new ReadyListener(botApiService, botEmojiScheduler);

            jda = JDABuilder.createDefault(Configuration.getInstance().getDotenv().get("BOT_TOKEN"))
                    .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .build()
                    .awaitReady();

            readyListener.initialize(jda);

            new ListenerManager(
                    commandManager,
                    botApiService,
                    catalogueCache,
                    catalogueMessageService,
                    emojiRefreshDebouncer
            ).registerListeners(jda);

            JDA readyJda = jda;
            BotEmojiScheduler readyBotEmojiScheduler = botEmojiScheduler;
            GuildEmojiRefreshDebouncer readyEmojiRefreshDebouncer = emojiRefreshDebouncer;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(readyBotEmojiScheduler, readyEmojiRefreshDebouncer, readyJda)));
        } catch (InterruptedException e) {
            shutdown(botEmojiScheduler, emojiRefreshDebouncer, jda);
            Thread.currentThread().interrupt();
            logger.error("Le thread a été interrompu : {}", e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException exception) {
            shutdown(botEmojiScheduler, emojiRefreshDebouncer, jda);
            logger.error("Configuration invalide : {}", exception.getMessage());
            System.exit(1);
        } catch (ApiException exception) {
            shutdown(botEmojiScheduler, emojiRefreshDebouncer, jda);
            logger.error("Erreur API au démarrage : {} ({})", exception.errorCode(), exception.statusCode());
            System.exit(1);
        } catch (Exception e) {
            shutdown(botEmojiScheduler, emojiRefreshDebouncer, jda);
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
}
