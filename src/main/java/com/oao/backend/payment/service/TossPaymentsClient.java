package com.oao.backend.payment.service;

import com.oao.backend.common.BusinessException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

@Service
public class TossPaymentsClient {
  private final RestClient client;
  private final String secret, clientKey, front;

  public TossPaymentsClient(
      @Value("${oao.payment.toss.secret-key:}") String secret,
      @Value("${oao.payment.toss.client-key:}") String clientKey,
      @Value("${oao.payment.frontend-url:http://localhost:3000}") String front) {
    this.secret = secret;
    this.clientKey = clientKey;
    this.front = front.replaceAll("/+$", "");
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(25));
    client =
        RestClient.builder()
            .baseUrl("https://api.tosspayments.com/v1")
            .requestFactory(factory)
            .defaultHeader(
                "Authorization",
                "Basic "
                    + Base64.getEncoder()
                        .encodeToString((secret + ":").getBytes(StandardCharsets.UTF_8)))
            .build();
  }

  public void requireConfigured() {
    if (secret.isBlank() || clientKey.isBlank())
      throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "결제 서비스 준비 중입니다. 운영자에게 문의해주세요.");
  }

  public String clientKey() {
    requireConfigured();
    return clientKey;
  }

  public String frontend() {
    return front;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> confirm(String key, String order, long amount) {
    requireConfigured();
    try {
      return client
          .post()
          .uri("/payments/confirm")
          .header("Idempotency-Key", order)
          .body(Map.of("paymentKey", key, "orderId", order, "amount", amount))
          .retrieve()
          .body(Map.class);
    } catch (RestClientException e) {
      try {
        var payment = get(key);
        if ("DONE".equals(payment.get("status"))) return payment;
      } catch (RuntimeException ignored) {
      }
      throw failure();
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> get(String key) {
    requireConfigured();
    try {
      return client.get().uri("/payments/{key}", key).retrieve().body(Map.class);
    } catch (RestClientException e) {
      throw failure();
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getByOrder(String order) {
    requireConfigured();
    try {
      return client.get().uri("/payments/orders/{order}", order).retrieve().body(Map.class);
    } catch (HttpClientErrorException.NotFound e) {
      return null;
    } catch (RestClientException e) {
      throw failure();
    }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> cancel(String key, String order, String reason) {
    requireConfigured();
    try {
      return client
          .post()
          .uri("/payments/{key}/cancel", key)
          .header("Idempotency-Key", "refund-" + order)
          .body(Map.of("cancelReason", reason))
          .retrieve()
          .body(Map.class);
    } catch (RestClientException e) {
      try {
        var result = get(key);
        if ("CANCELED".equals(result.get("status"))) return result;
      } catch (RuntimeException ignored) {
      }
      throw failure();
    }
  }

  private BusinessException failure() {
    return new BusinessException(
        HttpStatus.BAD_GATEWAY, "결제 상태를 확인하지 못했습니다. 결제 내역에서 다시 확인해주세요. 중복으로 결제하지 마세요.");
  }
}
