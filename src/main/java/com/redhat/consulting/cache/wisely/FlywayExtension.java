package com.redhat.consulting.cache.wisely;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterDeploymentValidation;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

@ApplicationScoped
public class FlywayExtension implements Extension {

  private final Logger LOG = Logger.getLogger(FlywayExtension.class);

  public void registerFlywayType(@Observes BeforeBeanDiscovery event) {
    event.addAnnotatedType(Flyway.class, Flyway.class.getName());
  }

  void afterBeanDiscovery(@Observes AfterDeploymentValidation event, BeanManager manager) {
    LOG.info("afterBeanDiscovery event received");
    try {
      Context ctx = new InitialContext();
      LOG.info("Initial JNDI Context");

      Object result = ctx.lookup("java:jboss/datasources/cachestore");
      LOG.info("Retrieved named JNDI Object");

      if (result instanceof DataSource) {
        var ds = (DataSource) result;
        LOG.info("Flyway Migration started");
        var flyway = Flyway.configure().dataSource(ds).load();
        flyway.migrate();
        LOG.info("Flyway Migration completed");
      } else {
        LOG.warn("Unable to retrieve DataSource from JNDI lookup");
      }
    } catch (NamingException ne) {
      LOG.error("Unable to lookup DataSource via JNDI", ne);
    }
  }
}
