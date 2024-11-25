package org.camelia.studio.gachamelia.repositories;

import org.camelia.studio.gachamelia.db.HibernateConfig;
import org.camelia.studio.gachamelia.models.Stat;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.models.UserStat;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

public class StatRepository {
    private static StatRepository instance;
    private final SessionFactory sessionFactory;

    public StatRepository() {
        this.sessionFactory = HibernateConfig.getSessionFactory();
    }

    public static StatRepository getInstance() {
        if (instance == null) {
            instance = new StatRepository();
        }

        return instance;
    }

    public List<Stat> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Stat", Stat.class).list();
        }
    }

    public List<UserStat> getUserStats(User user) {
        try (Session session = sessionFactory.openSession()) {

            return session.createQuery("FROM UserStat us WHERE us.id.user.id = :userId", UserStat.class)
                    .setParameter("userId", user.getId())
                    .getResultList();
        }
    }

    public void saveUserStat(UserStat userStat) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(userStat);
            session.getTransaction().commit();
        }
    }
}
