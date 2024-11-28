package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.models.Rank;
import org.camelia.studio.gachamelia.models.Stat;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.models.UserStat;
import org.camelia.studio.gachamelia.repositories.ElementRepository;
import org.camelia.studio.gachamelia.repositories.RankRepository;
import org.camelia.studio.gachamelia.repositories.RoleRepository;
import org.camelia.studio.gachamelia.repositories.StatRepository;
import org.camelia.studio.gachamelia.services.ElementService;
import org.camelia.studio.gachamelia.services.RankService;
import org.camelia.studio.gachamelia.services.RoleService;
import org.camelia.studio.gachamelia.services.UserService;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ReadyListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);


    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        logger.info("Connecté en tant que {}", event.getJDA().getSelfUser().getAsTag());
        initDatabase(event.getJDA());
    }

    private void initDatabase(JDA jda) {
        if (!RankRepository.getInstance().findAll().isEmpty() || !RoleRepository.getInstance().findAll().isEmpty() || !ElementRepository.getInstance().findAll().isEmpty() || !StatRepository.getInstance().findAll().isEmpty()) {
            Guild guild = jda.getGuildById(Configuration.getInstance().getDotenv().get("GUILD_ID"));
            Rank rankStaff = RankRepository.getInstance().getRankStaff();

            if (rankStaff == null) {
                logger.error("Aucun rang staff n'a été trouvé dans la base de données");
                System.exit(1);
            }

            if (guild != null) {
                guild.loadMembers().onSuccess(members -> {
                    for (Member member : members) {
                        User user = UserService.getInstance().getOrCreateUser(member.getId());

                        Role role = guild.getRoleById(Configuration.getInstance().getDotenv().get("STAFF_ROLE"));

                        if (role != null && member.getRoles().contains(role) && (user.getRank() == null || !user.getRank().isStaff())) {
                            user.setRank(rankStaff);
                            UserService.getInstance().updateUser(user);
                        }

                        if (user.getRank() == null) {
                            user.setRank(RankService.getInstance().getRandomRank());
                            UserService.getInstance().updateUser(user);
                        }

                        if (user.getRole() == null) {
                            user.setRole(RoleService.getInstance().getRandomRole());
                            UserService.getInstance().updateUser(user);
                        }

                        if (user.getElement() == null) {
                            user.setElement(ElementService.getInstance().getRandomElement());
                            UserService.getInstance().updateUser(user);
                        }

                        List<UserStat> stats = StatRepository.getInstance().getUserStats(user);

                        if (stats.isEmpty()) {
                            List<Stat> statsList = StatRepository.getInstance().findAll();
                            for (Stat stat : statsList) {
                                UserStat userStat = new UserStat();
                                userStat.setUser(user);
                                userStat.setStat(stat);
                                userStat.setValue(0);
                                StatRepository.getInstance().saveUserStat(userStat);
                            }
                        }

                        Rank rank = user.getRank();
                        Role discordRole = guild.getRoleById(rank.getDiscordId());

                        if (discordRole != null) {
                            guild.addRoleToMember(member, discordRole).queue();
                        }

                        logger.info("Utilisateur {} initialisé", member.getUser().getAsTag());
                    }
                });
            }
        } else {
            logger.error("Aucun rang ou rôle n'a été trouvé dans la base de données");
            System.exit(1);
        }
    }
}
