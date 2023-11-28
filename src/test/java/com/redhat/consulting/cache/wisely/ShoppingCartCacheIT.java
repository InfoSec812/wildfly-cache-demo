package com.redhat.consulting.cache.wisely;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.String.format;

public class ShoppingCartCacheIT {
  public static final int CLIENT_COUNT = 100;
  private SSLContext sc;

  private final List<String> hostList = List.of("192.168.100.10", "192.168.100.15", "192.168.100.20");
  Random rand = new Random();

  @Test
  public void initializeThreads() throws InterruptedException {
    System.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());

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
    try {
      sc = SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());

      List<Thread> threads = new ArrayList<>();

      loadUpThreads(threads, CLIENT_COUNT);

      while (threads.stream().anyMatch(Thread::isAlive)) {
        int threadCount = (int) threads.stream().filter(Thread::isAlive).count();
        System.err.printf("MAIN - Live thread count: %d%n", threadCount);
        if (threadCount < CLIENT_COUNT) {
          loadUpThreads(threads, (CLIENT_COUNT - threadCount));
        }
        synchronized (this) {
          this.wait(5000);
        }
      }

    } catch (GeneralSecurityException e) {
      e.printStackTrace();
    }
  }

  private void loadUpThreads(List<Thread> threads, int threadCount) {
    for (int i = 0; i < threadCount; i++) {
      try {
        var runnable = createNewClientThread();
        var newThread = new Thread(runnable);
        newThread.setDaemon(true);
        threads.add(newThread);
        newThread.start();
      } catch (NoSuchAlgorithmException nsae) {
        nsae.printStackTrace();
      }
    }
  }

  private CartLoadThread createNewClientThread() throws NoSuchAlgorithmException {

    String host = hostList.get(rand.nextInt(hostList.size()));
    String baseUrl = format("http://%s:8080/cache-wisely/api/v1", host);

    return CartLoadThread.builder()
                   .baseUrl(CartLoadThread.DEFAULT_URL)
                   .duration(Duration.of(2, ChronoUnit.HOURS))
                   .sslContext(sc)
                   .build();
  }

}
