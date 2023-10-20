package com.redhat.consulting.cache.wisely;

import lombok.Builder;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbException;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CartLoadThread implements Runnable {

  public static final String DEFAULT_URL = "http://192.168.100.60:8080/cache-wisely/api/v1";

  private static final int ITEM_COUNT = 40;

  private final String baseUrl;

  private final CookieManager cookieMgr;

  private final Duration duration;

  private final Jsonb jsonb;

  private final SSLContext sslContext;

  @Builder
  public CartLoadThread(Duration duration, String baseUrl, SSLContext sslContext) {
    super();
    this.duration = duration;
    this.baseUrl = baseUrl == null ? DEFAULT_URL : baseUrl;
    this.sslContext = sslContext;
    cookieMgr = new CookieManager();
    jsonb = JsonbBuilder.create();
  }

  @Override
  public void run() {
    Instant start = Instant.now();
    var rand = new Random();
    System.err.println("Starting new thread");

    try {

      var client = HttpClient.newBuilder()
                     .cookieHandler(cookieMgr)
                     .sslContext(sslContext)
                     .build();

      // Load an initial cart with 40 items
      String randomSkusUri = format("%s/items?count=%d&randomize=true", baseUrl, ITEM_COUNT);
      System.err.printf("Make request for random SKUs: %s%n%n", randomSkusUri);
      var skuItems = HttpRequest.newBuilder(URI.create(randomSkusUri))
                       .build();

      HttpResponse<String> randSkuResponse = client.send(skuItems, HttpResponse.BodyHandlers.ofString());
      System.err.printf("Response Status: %d%n", randSkuResponse.statusCode());
      var skuJsonResponse = randSkuResponse.body();

      JsonArray skuArray = jsonb.fromJson(skuJsonResponse, JsonArray.class);
      var skuList = skuArray
                      .getValuesAs(JsonObject.class)
                      .stream()
                      .map(o -> o.getString("sku"))
                      .collect(Collectors.joining(","));
      var addReq = HttpRequest
                     .newBuilder(URI.create(format("%s/cart/bulk?skus=%s", baseUrl, encode(skuList, UTF_8))))
                     .GET()
                     .build();
      var addSkusResponse = client.send(addReq, HttpResponse.BodyHandlers.ofString());
      System.err.printf("Add SKUs status: {}", addSkusResponse.statusCode());
      System.err.printf("Added multiple SKUs '%s' to cart%n", skuList);
      client = null;

      while (Duration.between(start, Instant.now()).compareTo(duration) <= 0) {

        client = HttpClient.newBuilder()
                       .cookieHandler(cookieMgr)
                       .sslContext(sslContext)
                       .build();
        Thread.sleep(1000 + rand.nextInt(100));

        // Get current cart contents
        var getCart = HttpRequest.newBuilder()
                        .uri(URI.create(format("%s/cart", baseUrl))).build();

        var cartResponse = client.send(getCart, HttpResponse.BodyHandlers.ofString()).body();
        var cartData = jsonb.fromJson(cartResponse, ShoppingCart.class);
        System.err.printf("Retrieved cart with '%d' items%n", cartData.getItems().size());

        // Select a random new SKU to add to the cart
        var getRandomItem = HttpRequest
                              .newBuilder(URI.create(format("%s/items?count=1&randomize=true", baseUrl)))
                              .GET()
                              .build();
        var itemResponse = client.send(getRandomItem, HttpResponse.BodyHandlers.ofString()).body();
        var itemData = jsonb.fromJson(itemResponse, JsonArray.class);
        var newSku = itemData.get(0).asJsonObject().getString("sku");

        System.err.printf("Adding item with SKU '%s' to cart%n", newSku);

        // Add new SKU to cart
        var addSku = HttpRequest.newBuilder()
                       .uri(URI.create(format("%s/cart/%s", baseUrl, newSku)))
                       .GET()
                       .build();
        client.send(addSku, HttpResponse.BodyHandlers.ofString());

        // Delete a random item from the cart
        var keyList = new ArrayList<>(cartData.getItems().keySet());

        System.err.printf("Number of keys: %d%n", keyList.size());

        var skuToDelete = keyList.get(rand.nextInt(keyList.size()));
        var deleteCartItem = HttpRequest
                               .newBuilder(URI.create(format("%s/cart/%s", baseUrl, skuToDelete)))
                               .DELETE()
                               .build();
        client.send(deleteCartItem, HttpResponse.BodyHandlers.ofString());

        System.err.printf("Deleted item with SKU '%s' from the cart%n", skuToDelete);
        client = null;
      }
    } catch(IOException ioe) {
      System.err.printf("IOException: %s%n", ioe.getLocalizedMessage());
      ioe.printStackTrace();
    } catch(InterruptedException ie) {
      System.err.printf("InterruptedException: %s%n", ie.getLocalizedMessage());
      ie.printStackTrace();
    } catch (JsonbException je) {
      System.err.printf("JsonbException: %s%n", je.getLocalizedMessage());
      je.printStackTrace();
    }
    System.err.printf("Thread ended: %s%n", Thread.currentThread().getName());
  }
}
