package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import jakarta.annotation.Nonnull;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.repossitories.RankRepository;
import org.camelia.studio.gachamelia.services.RankService;
import org.camelia.studio.gachamelia.services.UserService;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);


    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        logger.info("Connecté en tant que {}", event.getJDA().getSelfUser().getAsTag());
        initDatabase(event.getJDA());
    }

    private void initDatabase(JDA jda) {
        if (!RankRepository.getInstance().findAll().isEmpty()) {
            Guild guild = jda.getGuildById(Configuration.getInstance().getDotenv().get("GUILD_ID"));
            if (guild != null) {
                guild.loadMembers().onSuccess(members -> {
                    for (Member member : members) {
                        User user = UserService.getInstance().getOrCreateUser(member.getId());

                        if (user.getRank() == null) {
                            user.setRank(RankService.getInstance().getRandomRank());
                            UserService.getInstance().updateUser(user);
                        }

                        logger.info("Utilisateur {} initialisé", member.getUser().getAsTag());
                    }
                });
            }
        } else {
            logger.error("Aucun rang n'a été trouvé dans la base de données");
            System.exit(1);
        }
    }
}
