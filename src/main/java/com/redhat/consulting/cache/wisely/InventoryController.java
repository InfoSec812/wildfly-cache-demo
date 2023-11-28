package com.redhat.consulting.cache.wisely;

import org.jboss.resteasy.annotations.cache.Cache;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@RequestScoped
@Path("/items")
@Produces(value = MediaType.APPLICATION_JSON)
@Consumes(value = MediaType.APPLICATION_JSON)
public class InventoryController {

  @Inject
  InventoryDAO dao;

  @GET
  public List<Item> getItems(@QueryParam("skip") @DefaultValue("0") int skipCount, @QueryParam("count") @DefaultValue("-1") int resultCount, @QueryParam("randomize") @DefaultValue("false") boolean randomize) {
    return dao.getItems(resultCount, skipCount, randomize);
  }

  @GET
  @Path("/{sku}")
  @Cache
  public Item getItemBySku(@PathParam("sku") String sku) {
    return dao.getItemBySku(sku);
  }

  @GET
  @Path("/name")
  @Cache
  public List<Item> getItemByNameContains(@QueryParam("contains") String contains) {
    if (contains == null) {
      throw new BadRequestException("Required query paramter 'contains' is missing");
    }
    return dao.getItemsByNameContaining(contains);
  }

  @GET
  @Path("/desc")
  @Cache
  public List<Item> getItemByDescContains(@QueryParam("contains") String contains) {
    if (contains == null) {
      throw new BadRequestException("Required query paramter 'contains' is missing");
    }
    return dao.getItemsByDescContaining(contains);
  }
}
