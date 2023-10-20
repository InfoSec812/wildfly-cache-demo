package com.redhat.consulting.cache.wisely;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

import java.io.Serializable;

@Entity
@Table(name = "items")
@Data
public class Item implements Serializable {

  @Id
  private String sku;

  private float price;

  private String name;

  private String description;

  private int quantityInStock = 0;
}
