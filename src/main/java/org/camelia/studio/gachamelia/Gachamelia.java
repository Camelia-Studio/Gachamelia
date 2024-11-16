package org.camelia.studio.gachamelia;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.camelia.studio.gachamelia.db.HibernateConfig;
import org.camelia.studio.gachamelia.listeners.ReadyListener;
import org.camelia.studio.gachamelia.managers.ListenerManager;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.repossitories.RankRepository;
import org.camelia.studio.gachamelia.services.RankService;
import org.camelia.studio.gachamelia.services.UserService;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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



            // Initialisation de la base de données
            initDatabase();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                HibernateConfig.shutdown();
                jda.shutdown();
            }));
        } catch (Exception e) {
            logger.error("Une erreur est survenue lors de l'exécution du bot : {}", e.getMessage());
            System.exit(1);
        }
    }

    public static JDA getJda() {
        return jda;
    }

    public static void initDatabase() {
        if (!RankRepository.getInstance().findAll().isEmpty()) {
            Guild guild = jda.getGuildById(Configuration.getInstance().getDotenv().get("GUILD_ID"));
            if (guild != null) {
                List<Member> members = guild.getMembers();

                for (Member member : members) {
                    User user = UserService.getInstance().getOrCreateUser(member.getId());

                    if (user.getRank() == null) {
                        user.setRank(RankService.getInstance().getRandomRank());
                        UserService.getInstance().updateUser(user);
                    }

                    logger.info("Utilisateur {} initialisé", member.getUser().getAsTag());
                }
            }
        } else {
            logger.error("Aucun rang n'a été trouvé dans la base de données");
            System.exit(1);
        }
    }

}