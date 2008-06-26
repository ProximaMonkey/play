package play.db.jpa;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Properties;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.hibernate.ejb.Ejb3Configuration;
import play.Logger;
import play.Play;
import play.db.DB;

public class JPA {

    public static EntityManagerFactory entityManagerFactory = null;

    public static void init() {
        if (Play.configuration.getProperty("jpa", "disabled").equals("enabled") && (entityManagerFactory == null)) {
            List<Class> classes = Play.classloader.getAllClasses();
            init(classes, Play.configuration);
        }
    }
 
    public static boolean isEnabled() {
        return Play.configuration.getProperty("jpa", "disabled").equals("enabled");
    }

    public static void init(List<Class> classes, Properties p) {
        if(DB.datasource == null) {
            Logger.fatal("Cannot enable JPA without a valid database");
            Play.configuration.setProperty("jpa", "disabled");
            return;
        }
        Ejb3Configuration cfg = new Ejb3Configuration();
        cfg.setDataSource(DB.datasource);
        cfg.setProperty("hibernate.hbm2ddl.auto", Play.configuration.getProperty("jpa.ddl", "update"));
        cfg.setProperty("hibernate.dialect", getDefaultDialect(p.getProperty("db.driver")));
        cfg.setProperty("javax.persistence.transaction", "RESOURCE_LOCAL");
        try {
            Field field = cfg.getClass().getDeclaredField("overridenClassLoader");
            field.setAccessible(true);
            field.set(cfg, Play.classloader);
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (Class clazz : classes) {            
            if (clazz.isAnnotationPresent(Entity.class)) {
                cfg.addAnnotatedClass(clazz);
                Logger.debug("JPA Model : %s", clazz);
            }
        }
        Logger.info("Initializing JPA ...");
        entityManagerFactory = cfg.buildEntityManagerFactory();
    }

    public static void shutdown() {
        if(entityManagerFactory != null) {
            entityManagerFactory.close();
            entityManagerFactory = null;
        }
    }

    public static EntityManager getEntityManager() {
        return entityManagerFactory.createEntityManager();
    }

    public static String getDefaultDialect(String driver) {
        if (driver.equals("org.hsqldb.jdbcDriver")) {
            return "org.hibernate.dialect.HSQLDialect";
        } else {
            String dialect = Play.configuration.getProperty("jpa.dialect");
            if(dialect != null) {
                return dialect;
            }
            throw new UnsupportedOperationException("I do not know which hibernate dialect to use with " +
                    driver + ", use the property jpa.dialect in config file");
        }
    }

    public static void startTx(boolean readonly) {
        if(!isEnabled()) {
            return;
        }
        EntityManager manager = entityManagerFactory.createEntityManager();
        manager.getTransaction().begin();
        JPAContext.createContext(manager, readonly);
    }

    public static void closeTx(boolean rollback) {
        if(!isEnabled() || JPAContext.local.get() == null) {
            return;
        }
        EntityManager manager = JPAContext.get().entityManager;
        if (JPAContext.get().readonly || rollback) {
            manager.getTransaction().rollback();
        } else {
            manager.getTransaction().commit();
        }
        JPAContext.clearContext();
    }
}