package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.models.*;
import org.camelia.studio.gachamelia.repositories.StatRepository;
import org.camelia.studio.gachamelia.repositories.UserRepository;

import java.util.List;

public class UserService {
    private static UserService instance;

    public static UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }

        return instance;
    }

    public User getOrCreateUser(String discordId) {
        User user = UserRepository.getInstance().findByDiscordId(discordId);

        if (user == null) {
            Rank rank = RankService.getInstance().getRandomRank();
            user = new User(discordId, rank);

            Role role = RoleService.getInstance().getRandomRole();
            user.setRole(role);

            Element element = ElementService.getInstance().getRandomElement();
            user.addElement(element);
            UserRepository.getInstance().save(user);

            List<Stat> stats = StatRepository.getInstance().findAll();
            for (Stat stat : stats) {
                UserStat userStat = new UserStat();
                userStat.setUser(user);
                userStat.setStat(stat);
                userStat.setValue(0);
                StatRepository.getInstance().saveUserStat(userStat);
            }
        }

        return user;
    }

    public List<User> getAllUsers() {
        return UserRepository.getInstance().findAll();
    }

    public void updateUser(User user) {
        UserRepository.getInstance().update(user);
    }
}
