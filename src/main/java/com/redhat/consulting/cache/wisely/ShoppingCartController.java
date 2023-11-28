package com.redhat.consulting.cache.wisely;

import io.opentracing.Span;
import io.opentracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import javax.validation.constraints.PositiveOrZero;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.Arrays;

import static java.lang.String.format;

@RequestScoped
@Path("/cart")
@Produces(value = MediaType.APPLICATION_JSON)
@Consumes(value = MediaType.APPLICATION_JSON)
public class ShoppingCartController {

  private static final Logger LOG = LoggerFactory.getLogger(ShoppingCartController.class);

  @Inject
  HttpServletRequest request;

  @Inject
  InventoryDAO dao;

  @Inject
  Tracer tracer;

  @GET
  public ShoppingCart getShoppingCart(@Context HttpServletRequest req) {
    return readCartFromCache();
  }

  @GET
  @Path("/{sku}")
  public CartItem addItemToCartWithGet(@Context HttpServletRequest req, @PathParam("sku") String sku, @QueryParam("quantity") @DefaultValue("1") @PositiveOrZero int quantity) {
    return addItemToCart(req, sku, quantity);
  }

  @POST
  @Path("/{sku}")
  @Transactional
  public CartItem addItemToCart(@Context HttpServletRequest req, @PathParam("sku") String sku, @QueryParam("quantity") @DefaultValue("1") @PositiveOrZero int quantity) {
    LOG.info("Adding SKU '{}' and quantity '{}' to session with ID '{}'", sku, quantity, req.getSession().getId());

    var cart = readCartFromCache();

    var item = addSkuToCartById(sku, quantity, cart);
    item.setQuantity(quantity);
    cart.addItem(item);
    writeCartToCache(cart);

    return item;
  }

  /**
   * Add an item to the {@link ShoppingCart} or increment the quantity if it already exists
   * @param sku The SKU as a String
   * @param quantity The Quantity to add to the cart
   * @param cart The {@link ShoppingCart} object instance
   * @return The {@link CartItem} which was added/updated
   */
  private CartItem addSkuToCartById(String sku, int quantity, ShoppingCart cart) {
    var item = cart.getItemBySku(sku);
    if (item == null) {
      Item inventoryItem = dao.getItemBySku(sku);
      if (inventoryItem == null) {
        throw new NotFoundException(format("Item with SKU '%s' not found.", sku));
      }
      LOG.info("Adding item with SKU '{}' and quantity '{}' to cart", sku, quantity);
      item = new CartItem(inventoryItem, quantity);
    } else {
      LOG.info("Increasing quantity of SKU '{}' with quantity '{}'", sku, quantity);
      item.increaseQuantity(quantity);
    }
    cart.getItems().put(sku, item);
    return item;
  }

  /**
   * Accept a comma-separated list of SKUs, iterate over the list and add them to the ShoppingCart
   * @param skuList A comma-separated list of SKUs
   */
  @GET
  @Path("/bulk")
  @Transactional
  public ShoppingCart addMultipleSkusToCart(@Context HttpServletRequest req, @QueryParam("skus") String skuList) {
    LOG.info("Adding SKUs {} to session with ID {}", skuList, req.getSession().getId());
    var cart = readCartFromCache();

    var ids = Arrays.asList(skuList.split(","));
    dao.getItemsByIdList(ids).stream().forEach(i -> cart.addItem(new CartItem(i, 0)));

    writeCartToCache(cart);
    return cart;
  }

  /**
   * Write the cart to the cache and capture the metrics
   * @param cart The {@link ShoppingCart} object instance
   */
  public void writeCartToCache(ShoppingCart cart) {
    Span span = tracer.buildSpan("writeCartToCache")
      .asChildOf(tracer.activeSpan())
      .start();
    cart.setLastModified(Instant.now().toEpochMilli());
    request.getSession().setAttribute("cart", cart);
    span.finish();
  }

  /**
   * Read the {@link ShoppingCart} object instance from the session cache
   * @return The {@link ShoppingCart} object instance retrieve or a new instance if one does not yet exist
   */
  public ShoppingCart readCartFromCache() {
    Span span = tracer.buildSpan("readCartFromCache")
      .asChildOf(tracer.activeSpan())
      .start();
    ShoppingCart cart = (ShoppingCart) request.getSession().getAttribute("cart");
    if (cart == null) {
      cart = new ShoppingCart();
    }
    span.finish();
    return cart;
  }

  @DELETE
  @Path("/{sku}")
  @Transactional
  public void removeFromCart(@Context HttpServletRequest req, @PathParam("sku") String sku) {
    LOG.info("Removing SKU {} from session with ID {}", sku, req.getSession().getId());

    ShoppingCart cart = readCartFromCache();

    cart.removeItemBySku(sku);

    writeCartToCache(cart);
  }
}
