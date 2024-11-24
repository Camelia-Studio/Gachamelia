package org.camelia.studio.gachamelia;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.camelia.studio.gachamelia.db.HibernateConfig;
import org.camelia.studio.gachamelia.listeners.ReadyListener;
import org.camelia.studio.gachamelia.managers.ListenerManager;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Gachamelia {
    private static JDA jda;
    private static final Logger logger = LoggerFactory.getLogger(Gachamelia.class);

    public static void main(String[] args) {
        try {
            Configuration.getInstance();

            jda = JDABuilder.createDefault(Configuration.getInstance().getDotenv().get("BOT_TOKEN"))
                    .addEventListeners(new ReadyListener())
                    .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                    .build()
                    .awaitReady()
            ;

            new ListenerManager().registerListeners(jda);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
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