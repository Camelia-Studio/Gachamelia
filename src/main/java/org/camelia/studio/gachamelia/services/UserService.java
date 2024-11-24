package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.models.Element;
import org.camelia.studio.gachamelia.models.Rank;
import org.camelia.studio.gachamelia.models.Role;
import org.camelia.studio.gachamelia.models.User;
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
            user.setElement(element);

            UserRepository.getInstance().save(user);
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
