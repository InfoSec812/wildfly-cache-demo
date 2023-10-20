package com.redhat.consulting.cache.wisely;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

@ApplicationScoped
@Default
public class InventoryDAOImpl implements InventoryDAO {

  private final EntityManager em;

  @Inject
  public InventoryDAOImpl(EntityManager em) {
    super();
    this.em = em;
  }

  public Item getItemBySku(String sku) {
    return em.find(Item.class, sku);
  }

  @Override
  public List<Item> getItems() {
    return em
             .createQuery("SELECT i FROM Item i", Item.class)
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
}
