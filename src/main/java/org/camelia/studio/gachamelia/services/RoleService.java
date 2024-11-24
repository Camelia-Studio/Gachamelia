package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.models.Role;
import org.camelia.studio.gachamelia.repositories.RoleRepository;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RoleService {
    private static RoleService instance;

    public static RoleService getInstance() {
        if (instance == null) {
            instance = new RoleService();
        }

        return instance;
    }

    public Role getRandomRole() {
        List<Role> roles = RoleRepository.getInstance().findAll();

        int percentage = ThreadLocalRandom.current().nextInt(100);
        int cumulativePercentage = 0;

        for (Role role : roles) {
            cumulativePercentage += role.getPercentage();
            if (percentage <= cumulativePercentage) {
                return role;
            }
        }

        // Ne devrait jamais arriver
        return null;
    }
}
