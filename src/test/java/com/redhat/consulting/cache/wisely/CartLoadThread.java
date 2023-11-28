package com.redhat.consulting.cache.wisely;

import lombok.Builder;
import org.postgresql.shaded.com.ongres.scram.common.bouncycastle.base64.Base64;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbException;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CartLoadThread implements Runnable {

  public static final String DEFAULT_URL = "http://192.168.100.60:8080/cache-wisely/api/v1";

  private static final int ITEM_COUNT = 4000;
  public static final int HTTP_REQUEST_TIMEOUT = 2000;

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
    // System.err.println("Starting new thread");
    var httpRequestTimeout = Duration.of(HTTP_REQUEST_TIMEOUT, ChronoUnit.MILLIS);

    String threadName = Thread.currentThread().getName();
    String randIdent = UUID.randomUUID().toString();

    try {

      var client = HttpClient.newBuilder()
                     .connectTimeout(Duration.of(5, ChronoUnit.SECONDS))
                     .followRedirects(HttpClient.Redirect.NORMAL)
                     .cookieHandler(cookieMgr)
                     .version(HttpClient.Version.HTTP_1_1)
                     .sslContext(sslContext)
                     .build();

      // Load an initial cart with 40 items
      String randomSkusUri = format("%s/items?count=%d&randomize=true", baseUrl, ITEM_COUNT);
      // System.err.printf("%s - Make request for random SKUs: %s%n%n", threadName, randomSkusUri);
      var skuItems = HttpRequest.newBuilder(URI.create(randomSkusUri))
                       .GET()
                       .header("X-Forwarded-For", randIdent)
                       .build();

      HttpResponse<String> randSkuResponse = client.send(skuItems, HttpResponse.BodyHandlers.ofString());
      System.err.printf("%s - Response Status: %d%n", threadName, randSkuResponse.statusCode());
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
                     .header("X-Forwarded-For", randIdent)
                     .build();
      var addSkusResponse = client.send(addReq, HttpResponse.BodyHandlers.ofString());

      if (addSkusResponse.statusCode() != 200) {
         System.err.printf("%s - Failed to initialize cart: %d%n", threadName, addSkusResponse.statusCode());
        return;
      }

      while (Duration.between(start, Instant.now()).compareTo(duration) <= 0) {

        if (rand.nextInt(100) > 98) {
          break;
        }
        try {
          synchronized (this) {
            this.wait(3000 + rand.nextInt(3000));
          }

          // Get current cart contents
          var getCart = HttpRequest.newBuilder()
                          .uri(URI.create(format("%s/cart", baseUrl)))
                          .GET()
                          .header("X-Forwarded-For", randIdent)
                          .timeout(httpRequestTimeout)
                          .build();

          var cartResponse = client.send(getCart, HttpResponse.BodyHandlers.ofString());
          if (cartResponse.statusCode() == 200) {
            var cartData = jsonb.fromJson(cartResponse.body(), ShoppingCart.class);
            if (!cartData.getItems().isEmpty()) {

              // Select a random new SKU to add to the cart
              var getRandomItem = HttpRequest
                                    .newBuilder(URI.create(format("%s/items?count=1&randomize=true", baseUrl)))
                                    .GET()
                                    .header("X-Forwarded-For", randIdent)
                                    .timeout(httpRequestTimeout)
                                    .build();
              var itemResponse = client.send(getRandomItem, HttpResponse.BodyHandlers.ofString()).body();
              var itemData = jsonb.fromJson(itemResponse, JsonArray.class);
              var newSku = itemData.get(0).asJsonObject().getString("sku");

              // System.err.printf("%s - Adding item with SKU '%s' to cart%n", threadName, newSku);

              // Add new SKU to cart
              var addSku = HttpRequest.newBuilder()
                             .uri(URI.create(format("%s/cart/%s", baseUrl, newSku)))
                             .GET()
                             .header("X-Forwarded-For", randIdent)
                             .timeout(httpRequestTimeout)
                             .build();
              client.send(addSku, HttpResponse.BodyHandlers.ofString());

              // Delete a random item from the cart
              var keyList = new ArrayList<>(cartData.getItems().keySet());

              // System.err.printf("%s - Number of keys: %d%n", threadName, keyList.size());

              var skuToDelete = keyList.get(rand.nextInt(keyList.size()));
              var deleteCartItem = HttpRequest
                                     .newBuilder(URI.create(format("%s/cart/%s", baseUrl, skuToDelete)))
                                     .DELETE()
                                     .header("X-Forwarded-For", randIdent)
                                     .timeout(httpRequestTimeout)
                                     .build();
              client.send(deleteCartItem, HttpResponse.BodyHandlers.ofString());

              // System.err.printf("%s - Deleted item with SKU '%s' from the cart%n", threadName, skuToDelete);
            } else {
               System.err.printf("%s - Retrieved cart with '%d' items%n", threadName, cartData.getItems().size());
            }
          } else {
            System.err.printf("%s - Cart retrieval failed%n", threadName);
          }
        } catch (HttpTimeoutException hte) {
          System.err.printf("%s - HttpTimeoutException: %s%n", threadName, hte.getLocalizedMessage());
        }
      }
    } catch(IOException ioe) {
      System.err.printf("%s - IOException: %s%n", threadName, ioe.getLocalizedMessage());
    } catch(InterruptedException ie) {
      System.err.printf("%s - InterruptedException: %s%n", threadName, ie.getLocalizedMessage());
    } catch (JsonbException je) {
      System.err.printf("%s - JsonbException: %s%n", threadName, je.getLocalizedMessage());
      je.printStackTrace();
    }
    System.err.printf("%s - Thread ended: %s%n", threadName, Thread.currentThread().getName());
  }
}
