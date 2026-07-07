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
    private static JDA jda;

    public static void main(String[] args) {
        try {
            Configuration.getInstance();
            ApiConfiguration apiConfiguration = ApiConfiguration.from(Configuration.getInstance());
            ApiTransport apiTransport = new JavaHttpApiTransport(HttpClient.newHttpClient());
            ApiTokenProvider tokenProvider = new ApiTokenProvider(apiConfiguration, apiTransport, Clock.systemUTC());
            GachameliaApiClient apiClient = new GachameliaApiClient(apiConfiguration, tokenProvider, apiTransport);
            GuildCatalogueCache catalogueCache = new GuildCatalogueCache();
            EmojiSnapshotService emojiSnapshotService = new EmojiSnapshotService();
            BotApiService botApiService = new BotApiService(apiClient, catalogueCache, emojiSnapshotService);
            BotEmojiScheduler botEmojiScheduler = new BotEmojiScheduler(apiClient, emojiSnapshotService);
            GuildEmojiRefreshDebouncer emojiRefreshDebouncer = new GuildEmojiRefreshDebouncer(apiClient, emojiSnapshotService, Duration.ofSeconds(3));
            CatalogueMessageService catalogueMessageService = new CatalogueMessageService(RandomGenerator.getDefault());
            CommandManager commandManager = new CommandManager(botApiService, catalogueCache);

            jda = JDABuilder.createDefault(Configuration.getInstance().getDotenv().get("BOT_TOKEN"))
                    .addEventListeners(new ReadyListener(botApiService, botEmojiScheduler))
                    .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .build()
                    .awaitReady();

            new ListenerManager(
                    commandManager,
                    botApiService,
                    catalogueCache,
                    catalogueMessageService,
                    emojiRefreshDebouncer
            ).registerListeners(jda);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(botEmojiScheduler, emojiRefreshDebouncer, jda)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Le thread a été interrompu : {}", e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException exception) {
            logger.error("Configuration invalide : {}", exception.getMessage());
            System.exit(1);
        } catch (ApiException exception) {
            logger.error("Erreur API au démarrage : {} ({})", exception.errorCode(), exception.statusCode());
            System.exit(1);
        } catch (Exception e) {
            logger.error("Une erreur est survenue lors de l'exécution du bot : {}", e.getMessage());
            System.exit(1);
        }
    }

    static void shutdown(BotEmojiScheduler botEmojiScheduler, GuildEmojiRefreshDebouncer emojiRefreshDebouncer, JDA jda) {
        botEmojiScheduler.close();
        emojiRefreshDebouncer.close();
        jda.shutdown();
    }

    public static JDA getJda() {
        return jda;
    }
}
