package com.redhat.consulting.cache.wisely;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import static java.lang.String.format;

@RequestScoped
@Path("/cart")
@Produces(value = MediaType.APPLICATION_JSON)
@Consumes(value = MediaType.APPLICATION_JSON)
public class ShoppingCartController {

  @Inject
  HttpServletRequest request;
  @Inject
  InventoryDAO dao;
  @Inject
  Tracer tracer;

/*  @Inject
  public ShoppingCartController(Tracer tracer, HttpServletRequest request, InventoryDAO inventoryDao) {
    super();
    this.request = request;
    this.dao = inventoryDao;
    this.tracer = tracer;
  }*/

  @GET
  public ShoppingCart getShoppingCart() {
    ShoppingCart cart = (ShoppingCart) request.getSession().getAttribute("cart");
    if (cart == null) {
      cart = new ShoppingCart();
    }

    return cart;
  }

  @POST
  @Path("/{sku}")
  public CartItem addItemToCart(@PathParam("sku") String sku, @QueryParam("quantity") @DefaultValue("1") @PositiveOrZero int quantity) {
    Span cacheRetrieveSpan = tracer.spanBuilder("cache-retrieve").startSpan();
    cacheRetrieveSpan.makeCurrent();
    ShoppingCart cart = (ShoppingCart) request.getSession().getAttribute("cart");
    cacheRetrieveSpan.end();
    if (cart == null) {
      cart = new ShoppingCart();
    }

    CartItem item = cart.getItemBySku(sku);
    if (item == null) {
      Item inventoryItem = dao.getItemBySku(sku);
      if (inventoryItem == null) {
        throw new NotFoundException(format("Item with SKU '%s' not found.", sku));
      }
      item = new CartItem(inventoryItem, quantity);
    }
    item.setQuantity(quantity);
    Span cacheUpdateSpan = tracer.spanBuilder("cache-update").startSpan();
    cacheUpdateSpan.makeCurrent();
    cart.addItem(item);
    cacheUpdateSpan.end();

    request.getSession().setAttribute("cart", cart);
    return item;
  }

  @DELETE
  @Path("/{sku}")
  public void removeFromCart(@PathParam("sku") String sku) {
    ShoppingCart cart = (ShoppingCart) request.getSession().getAttribute("cart");
    if (cart == null) {
      throw new BadRequestException("The shopping cart has not been initialized and has no contents");
    }
    cart.removeItemBySku(sku);
    request.getSession().setAttribute("cart", cart);
  }
}
