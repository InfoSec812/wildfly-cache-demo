package com.redhat.consulting.cache.wisely;

import org.eclipse.microprofile.opentracing.Traced;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

import static java.lang.String.format;

@ApplicationScoped
@Default
@Traced
public class InventoryDAOImpl implements InventoryDAO {

  private final EntityManager em;

  private static final String BASE_QUERY = "FROM Item i";

  @Inject
  public InventoryDAOImpl(EntityManager em) {
    super();
    this.em = em;
  }

  public Item getItemBySku(String sku) {
    return em.find(Item.class, sku);
  }

  @Override
  public List<Item> getItems(int limit, int offset, boolean randomize) {
    String query;
    if (randomize) {
      query = format("%s ORDER BY RANDOM()", BASE_QUERY);
    } else {
      query = BASE_QUERY;
    }
    return em
             .createQuery(query, Item.class)
             .setFirstResult(offset)
             .setMaxResults(limit)
             .getResultList();
  }

  @Override
  public List<Item> getItemsByDescContaining(String contains) {
    return em
             .createQuery("SELECT i FROM Item i WHERE i.description ILIKE :contains", Item.class)
             .setParameter("contains", "%"+contains+"%")
             .getResultList();
  }

  @Override
  public List<Item> getItemsByNameContaining(String contains) {
    return em
             .createQuery("SELECT i FROM Item i WHERE i.name ILIKE :contains", Item.class)
             .setParameter("contains", "%"+contains+"%")
             .getResultList();
  }

  @Override
  public List<Item> getItemsByIdList(List<String> ids) {
    return em.createQuery("FROM Item i WHERE i.sku IN :ids", Item.class)
      .setParameter("ids", ids)
      .getResultList();
  }
}
