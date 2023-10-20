package com.redhat.consulting.cache.wisely;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class CartItem implements Serializable {

  private Item item;

  private int quantity = 0;

  public void increaseQuantity(int quantity) {
    this.quantity += quantity;
  }

}
