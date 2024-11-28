package org.camelia.studio.gachamelia.db;

import io.github.cdimascio.dotenv.Dotenv;
import org.camelia.studio.gachamelia.interfaces.IEntity;
import org.camelia.studio.gachamelia.utils.ReflectionUtils;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

public class HibernateConfig {
    private static final Logger logger = LoggerFactory.getLogger(HibernateConfig.class);
    private static SessionFactory sessionFactory;

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                logger.info("Initializing Hibernate SessionFactory");
                Dotenv dotenv = org.camelia.studio.gachamelia.utils.Configuration.getInstance().getDotenv();

                Properties props = new Properties();

                // Configuration Hibernate
                props.put(Environment.HBM2DDL_AUTO, "update"); // On utilise validate au lieu de update


                props.put(Environment.GLOBALLY_QUOTED_IDENTIFIERS, "true");

                // Configuration HikariCP
                props.put("hibernate.connection.provider_class",
                        "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
                props.put("hibernate.hikari.minimumIdle", "5");
                props.put("hibernate.hikari.maximumPoolSize", "10");
                props.put("hibernate.hikari.idleTimeout", "300000");
                props.put("hibernate.hikari.dataSourceClassName",
                        "org.postgresql.ds.PGSimpleDataSource");
                props.put("hibernate.hikari.dataSource.url", dotenv.get("DB_URL"));
                props.put("hibernate.hikari.dataSource.user", dotenv.get("DB_USER"));
                props.put("hibernate.hikari.dataSource.password", dotenv.get("DB_PASSWORD"));

                Configuration configuration = new Configuration();
                configuration.setProperties(props);

                List<IEntity> entities = ReflectionUtils.loadClasses(
                        "org.camelia.studio.gachamelia.models",
                        IEntity.class
                );

                for (IEntity entity : entities) {
                    configuration.addAnnotatedClass(entity.getClass());
                }

                ServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties())
                        .build();

                sessionFactory = configuration.buildSessionFactory(serviceRegistry);
                logger.info("Hibernate SessionFactory initialized successfully");

            } catch (Exception e) {
                logger.error("Failed to initialize Hibernate SessionFactory", e);
                throw new RuntimeException("Failed to initialize Hibernate SessionFactory", e);
            }
        }
        return sessionFactory;
    }

    public static void shutdown() {
        logger.info("Shutting down database connections");
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            try {
                sessionFactory.close();
                logger.info("SessionFactory closed successfully");
            } catch (Exception e) {
                logger.error("Error closing SessionFactory", e);
            }
        }
    }
}