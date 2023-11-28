package com.redhat.consulting.cache.wisely;

import java.util.List;


public interface InventoryDAO {

  public Item getItemBySku(String sku);

	List<Item> getItems(int limit, int offset, boolean randomize);

  List<Item> getItemsByDescContaining(String contains);

  List<Item> getItemsByNameContaining(String contains);

  public List<Item> getItemsByIdList(List<String> ids);
}
