package com.redhat.consulting.cache.wisely;

import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.jboss.resteasy.annotations.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.PositiveOrZero;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

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
  @Metric(name = "get_cart", description = "Time (in nanoseconds) to read the card from the cache")
  Timer readTimer;

  @Inject
  @Metric(name = "add_to_cart", description = "Time (in nanoseconds) to save the cart to the cache")
  Timer writeTimer;


  @GET
  public ShoppingCart getShoppingCart(@Context HttpServletRequest req) {
    ShoppingCart cart = readCartFromCache();

    LOG.info("Request for session: {}", Arrays
                                                .stream(req.getCookies())
                                                .filter(c -> c.getName().compareTo("JSESSIONID") == 0)
                                                .map(Cookie::getValue)
                                                .findFirst()
                                                .orElse(""));

    return cart;
  }

  @GET
  @Path("/{sku}")
  public CartItem addItemToCartWithGet(@Context HttpServletRequest req, @PathParam("sku") String sku, @QueryParam("quantity") @DefaultValue("1") @PositiveOrZero int quantity) {
    return addItemToCart(req, sku, quantity);
  }

  @POST
  @Path("/{sku}")
  public CartItem addItemToCart(@Context HttpServletRequest req, @PathParam("sku") String sku, @QueryParam("quantity") @DefaultValue("1") @PositiveOrZero int quantity) {

    LOG.info("Request for session: {}", Arrays
                                                .stream(req.getCookies())
                                                .filter(c -> c.getName().compareTo("JSESSIONID") == 0)
                                                .map(Cookie::getValue)
                                                .findFirst()
                                                .orElse(""));
    LOG.info("Add item with SKU '{}' and quantity '{}' to cart", sku, quantity);
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
  public ShoppingCart addMultipleSkusToCart(@Context HttpServletRequest req, @QueryParam("skus") String skuList) {
    var cart = readCartFromCache();

    Stream.of(skuList.split(","))
      .forEach(sku -> addSkuToCartById(sku, 1, cart));

    writeCartToCache(cart);
    return cart;
  }

  /**
   * Write the cart to the cache and capture the metrics
   * @param cart The {@link ShoppingCart} object instance
   */
  private void writeCartToCache(ShoppingCart cart) {
    cart.setLastModified(Instant.now().toEpochMilli());
    Instant writeStart = Instant.now();
    request.getSession().setAttribute("cart", cart);
    Instant writeStop = Instant.now();
    writeTimer.update(Duration.between(writeStart, writeStop));
  }

  /**
   * Read the {@link ShoppingCart} object instance from the session cache
   * @return The {@link ShoppingCart} object instance retrieve or a new instance if one does not yet exist
   */
  private ShoppingCart readCartFromCache() {
    Instant readStart = Instant.now();
    ShoppingCart cart = (ShoppingCart) request.getSession().getAttribute("cart");
    Instant readStop = Instant.now();
    if (cart == null) {
      cart = new ShoppingCart();
    }
    readTimer.update(Duration.between(readStart, readStop));
    return cart;
  }

  @DELETE
  @Path("/{sku}")
  public void removeFromCart(@Context HttpServletRequest req, @PathParam("sku") String sku) {
    LOG.info("Request for session: {}", Arrays
                                          .stream(req.getCookies())
                                          .filter(c -> c.getName().compareTo("JSESSIONID") == 0)
                                          .map(Cookie::getValue)
                                          .findFirst()
                                          .orElse(""));
    ShoppingCart cart = readCartFromCache();

    cart.removeItemBySku(sku);

    writeCartToCache(cart);
  }
}
