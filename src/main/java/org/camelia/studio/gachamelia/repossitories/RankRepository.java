package org.camelia.studio.gachamelia.repossitories;


import org.camelia.studio.gachamelia.db.HibernateConfig;
import org.camelia.studio.gachamelia.models.Rank;
import org.camelia.studio.gachamelia.models.WelcomeMessage;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RankRepository {
    private final SessionFactory sessionFactory;
    private static RankRepository instance;

    public static RankRepository getInstance() {
        if (instance == null) {
            instance = new RankRepository();
        }

        return instance;
    }

    public RankRepository() {
        this.sessionFactory = HibernateConfig.getSessionFactory();
    }

    public List<Rank> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Rank", Rank.class).list();
        }
    }

    public Rank findByName(String name) {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM User WHERE name = :name", Rank.class)
                    .setParameter("discordId", name)
                    .uniqueResult();
        }
    }

    public Rank save(Rank rank) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.persist(rank);
            session.getTransaction().commit();
            return rank;
        }
    }

    public void update(Rank rank) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            session.merge(rank);
            session.getTransaction().commit();
        }
    }

    public WelcomeMessage getRandomWelcomeMessage(Rank rank) {
        try (Session session = sessionFactory.openSession()) {
            session.beginTransaction();
            try {
                // Recharger le rank avec ses messages
                Rank refreshedRank = session.get(Rank.class, rank.getId());
                List<WelcomeMessage> welcomeMessages = refreshedRank.getWelcomeMessages();

                if (welcomeMessages.isEmpty()) {
                    return null;
                }

                WelcomeMessage message = welcomeMessages.get(
                        ThreadLocalRandom.current().nextInt(welcomeMessages.size())
                );

                session.getTransaction().commit();
                return message;
            } catch (Exception e) {
                session.getTransaction().rollback();
                throw e;
            }
        }
    }
}
