package com.redhat.consulting.cache.wisely;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@RequestScoped
@Path("/items")
@Produces(value = MediaType.APPLICATION_JSON)
@Consumes(value = MediaType.APPLICATION_JSON)
public class InventoryController {

  @Inject
  InventoryDAO dao;

//  @Inject
//  public InventoryController(InventoryDAO dao) {
//    super();
//    this.dao = dao;
//  }

  @GET
  public List<Item> getItems() {
    return dao.getItems();
  }

  @GET
  @Path("/{sku}")
  public Item getItemBySku(@PathParam("sku") String sku) {
    return dao.getItemBySku(sku);
  }

  @GET
  @Path("/name")
  public List<Item> getItemByNameContains(@QueryParam("contains") String contains) {
    if (contains == null) {
      throw new BadRequestException("Required query paramter 'contains' is missing");
    }
    return dao.getItemsByNameContaining(contains);
  }

  @GET
  @Path("/desc")
  public List<Item> getItemByDescContains(@QueryParam("contains") String contains) {
    if (contains == null) {
      throw new BadRequestException("Required query paramter 'contains' is missing");
    }
    return dao.getItemsByDescContaining(contains);
  }
}
