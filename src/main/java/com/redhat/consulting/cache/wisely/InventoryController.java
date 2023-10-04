package com.redhat.consulting.cache.wisely;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Collections;
import java.util.List;
import java.util.Random;

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
  public List<Item> getItems(@QueryParam("skip") @DefaultValue("-1") int skipCount, @QueryParam("count") @DefaultValue("-1") int resultCount, @QueryParam("randomize") @DefaultValue("false") boolean randomize) {
    var result = dao.getItems();
    if (randomize) {
      Collections.shuffle(result, new Random(System.nanoTime()));
    }
    if (skipCount > 0) {
      int listLen = result.size() - 1;
      result = result.subList(skipCount, listLen);
    }
    if (resultCount > 0) {
      return result.stream().limit(resultCount).toList();
    }
    return result;
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
