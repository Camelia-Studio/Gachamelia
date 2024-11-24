package org.camelia.studio.gachamelia.repositories;

import org.camelia.studio.gachamelia.db.HibernateConfig;
import org.camelia.studio.gachamelia.models.Element;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

public class ElementRepository {
    private static ElementRepository instance;
    private final SessionFactory sessionFactory;

    public ElementRepository() {
        this.sessionFactory = HibernateConfig.getSessionFactory();
    }

    public static ElementRepository getInstance() {
        if (instance == null) {
            instance = new ElementRepository();
        }

        return instance;
    }

    public List<Element> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("FROM Element", Element.class).list();
        }
    }
}
