package com.redhat.consulting.cache.wisely;

import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

@ApplicationScoped
public class Resources {

    private final Logger LOG = Logger.getLogger(Resources.class);

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Produces
    @Default
    @RequestScoped
    public EntityManager createEntityManager() {
        return this.entityManagerFactory.createEntityManager();
    }
    public void dispose(@Disposes @Default EntityManager entityManager) {
        if (entityManager.isOpen()) {
            entityManager.close();
        }
    }
}
