package com.redhat.consulting.cache.wisely;

import org.junit.jupiter.api.Test;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class ShoppingCartCacheIT {

  private ExecutorService threadPool;

  private static final int CLIENT_COUNT = 20;

  @Test
  public void initializeThreads() throws InterruptedException {
    threadPool = Executors.newFixedThreadPool(CLIENT_COUNT,
      new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
          Thread t = Executors.defaultThreadFactory().newThread(r);
          t.setDaemon(true);
          return t;
        }
      });

    TrustManager[] trustAllCerts = new TrustManager[] {
      new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
        public void checkClientTrusted(
          java.security.cert.X509Certificate[] certs, String authType) {
        }
        public void checkServerTrusted(
          java.security.cert.X509Certificate[] certs, String authType) {
        }
      }
    };

    // Install the all-trusting trust manager
    SSLContext sc;
    try {
      sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      IntStream.of(CLIENT_COUNT).forEach(i -> this.createNewClientThread(sc));
      threadPool.awaitTermination(2, TimeUnit.HOURS);
    } catch (GeneralSecurityException e) {
      e.printStackTrace();
    }
  }

  private void createNewClientThread(SSLContext sc) {
    var thread = CartLoadThread.builder()
                   .baseUrl(CartLoadThread.DEFAULT_URL)
                   .duration(Duration.of(2, ChronoUnit.HOURS))
                   .sslContext(sc)
                   .build();
    threadPool.submit(thread);
  }

}
