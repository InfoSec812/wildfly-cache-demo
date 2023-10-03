package com.redhat.consulting.cache.wisely;

import java.util.List;

public interface InventoryDAO {

  public Item getItemBySku(String sku);

	List<Item> getItems();
  
  List<Item> getItemsByDescContaining(String contains);
  
  List<Item> getItemsByNameContaining(String contains);
}
