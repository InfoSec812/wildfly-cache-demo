package com.redhat.consulting.cache.wisely;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode
public class CartItem {

  private Item item;

  private int quantity = 0;

}
