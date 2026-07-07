package org.camelia.studio.gachamelia;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.camelia.studio.gachamelia.api.ApiConfiguration;
import org.camelia.studio.gachamelia.api.ApiTokenProvider;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.GachameliaApiClient;
import org.camelia.studio.gachamelia.api.http.ApiTransport;
import org.camelia.studio.gachamelia.api.http.JavaHttpApiTransport;
import org.camelia.studio.gachamelia.db.HibernateConfig;
import org.camelia.studio.gachamelia.listeners.ReadyListener;
import org.camelia.studio.gachamelia.managers.ListenerManager;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

public class Gachamelia {
    private static JDA jda;
    private static final Logger logger = LoggerFactory.getLogger(Gachamelia.class);

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

            jda = JDABuilder.createDefault(Configuration.getInstance().getDotenv().get("BOT_TOKEN"))
                    .addEventListeners(new ReadyListener(botApiService, botEmojiScheduler))
                    .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .build()
                    .awaitReady();

            new ListenerManager().registerListeners(jda);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                emojiRefreshDebouncer.close();
                botEmojiScheduler.close();
                HibernateConfig.shutdown();
                jda.shutdown();
            }));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Le thread a été interrompu : {}", e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            logger.error("Une erreur est survenue lors de l'exécution du bot : {}", e.getMessage());
            System.exit(1);
        }
    }

    public static JDA getJda() {
        return jda;
    }
}
