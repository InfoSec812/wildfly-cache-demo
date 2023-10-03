package com.redhat.consulting.cache.wisely;

import lombok.Data;

import java.beans.Transient;
import java.time.Instant;
import java.util.*;

@Data
public class ShoppingCart {

  private Instant created = Instant.now();

  private Instant lastModified = Instant.now();

  private Map<String, CartItem> items = new HashMap<>();

  @Transient
  public CartItem getItemBySku(String sku) {
    return this.items.get(sku);
  }

  public void removeItemBySku(String sku) {
    this.items.remove(sku);
  }

  public void addItem(CartItem item) {
    this.items.put(item.getItem().getSku(), item);
  }
}
