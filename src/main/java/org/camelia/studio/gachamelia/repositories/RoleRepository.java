package org.camelia.studio.gachamelia.repositories;

import org.camelia.studio.gachamelia.db.HibernateConfig;
import org.camelia.studio.gachamelia.models.Role;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

public class RoleRepository {
    private static RoleRepository instance;
    private final SessionFactory sessionFactory;

    public RoleRepository() {
        this.sessionFactory = HibernateConfig.getSessionFactory();
    }

    public static RoleRepository getInstance() {
        if (instance == null) {
            instance = new RoleRepository();
        }

        return instance;
    }

    public List<Role> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Role", Role.class).list();
        }
    }
}
