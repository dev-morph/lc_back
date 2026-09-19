package com.oao.backend.payment.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.auth.*;
import com.oao.backend.common.*;
import com.oao.backend.payment.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class TossPaymentController {
  private final CurrentUser current;
  private final TossPaymentService service;
  private final AdminAccessService admin;
  private final RequestRateLimiter rate;

  public TossPaymentController(
      CurrentUser current,
      TossPaymentService service,
      AdminAccessService admin,
      RequestRateLimiter rate) {
    this.current = current;
    this.service = service;
    this.admin = admin;
    this.rate = rate;
  }

  @PostMapping("/hearts/purchases/toss/prepare")
  ApiResponse<?> prepare(HttpServletRequest r, @RequestBody Prepare body) {
    Long id = current.require(r);
    rate.check("prepare:" + id, 20, 3600);
    return ApiResponse.ok(
        service.prepare(id, body.heartProductId(), body.meetingApplicationId(), body.returnTo()));
  }

  @PostMapping("/hearts/purchases/toss/confirm")
  ApiResponse<?> confirm(HttpServletRequest r, @Valid @RequestBody Confirm b) {
    return ApiResponse.ok(
        service.confirm(current.require(r), b.paymentKey(), b.orderId(), b.amount()));
  }

  @PostMapping("/hearts/purchases/toss/fail")
  ApiResponse<?> fail(HttpServletRequest r, @RequestBody Fail b) {
    service.fail(current.require(r), b.orderId(), b.message());
    return ApiResponse.ok();
  }

  @GetMapping("/me/payments")
  ApiResponse<?> list(HttpServletRequest r) {
    return ApiResponse.ok(service.list(current.require(r)));
  }

  @PostMapping("/me/payments/{id}/refresh")
  ApiResponse<?> refresh(HttpServletRequest r, @PathVariable Long id) {
    return ApiResponse.ok(service.reconcile(id, current.require(r)));
  }

  @PostMapping("/me/payments/{id}/cancel")
  ApiResponse<?> cancel(HttpServletRequest r, @PathVariable Long id) {
    service.cancelUnpaid(id, current.require(r));
    return ApiResponse.ok();
  }

  @GetMapping("/admin/payments")
  ApiResponse<?> adminList(HttpServletRequest r) {
    admin.requireActiveAdmin(r);
    return ApiResponse.ok(service.list(null));
  }

  @PostMapping("/admin/payments/{id}/refund")
  ApiResponse<?> refund(HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Refund b) {
    admin.requireActiveAdmin(r);
    service.refund(id, b.reason());
    return ApiResponse.ok();
  }

  @PostMapping("/admin/payments/{id}/refresh")
  ApiResponse<?> adminRefresh(HttpServletRequest r, @PathVariable Long id) {
    admin.requireActiveAdmin(r);
    return ApiResponse.ok(service.reconcile(id, null));
  }

  @PostMapping("/payments/toss/webhook")
  ApiResponse<?> webhook(HttpServletRequest r, @RequestBody Map<String, Object> body) {
    rate.check("webhook:" + r.getRemoteAddr(), 120, 60);
    Object raw = body.get("data");
    if (raw instanceof Map<?, ?> data)
      service.webhook((String) data.get("paymentKey"), (String) data.get("orderId"));
    return ApiResponse.ok();
  }

  record Prepare(Long heartProductId, Long meetingApplicationId, String returnTo) {}

  record Confirm(
      @NotBlank @Size(max = 200) String paymentKey,
      @NotBlank @Size(max = 64) String orderId,
      @Positive long amount) {}

  record Fail(String orderId, String message) {}

  record Refund(@NotBlank @Size(max = 500) String reason) {}
}
