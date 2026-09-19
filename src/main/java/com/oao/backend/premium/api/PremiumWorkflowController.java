package com.oao.backend.premium.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.auth.CurrentUser;
import com.oao.backend.common.*;
import com.oao.backend.premium.service.PremiumWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PremiumWorkflowController {
  private final CurrentUser user;
  private final PremiumWorkflowService service;
  private final AdminAccessService admin;

  public PremiumWorkflowController(
      CurrentUser user, PremiumWorkflowService service, AdminAccessService admin) {
    this.user = user;
    this.service = service;
    this.admin = admin;
  }

  @GetMapping("/personality-keywords")
  ApiResponse<?> keywords() {
    return ApiResponse.ok(service.keywords());
  }

  @GetMapping("/me/premium-introductions")
  ApiResponse<?> list(HttpServletRequest r) {
    return ApiResponse.ok(service.list(user.require(r)));
  }

  @GetMapping("/me/premium-introductions/{id}")
  ApiResponse<?> detail(HttpServletRequest r, @PathVariable Long id) {
    return ApiResponse.ok(service.detail(id, user.require(r)));
  }

  @DeleteMapping("/me/premium-introductions/{id}")
  ApiResponse<?> cancel(HttpServletRequest r, @PathVariable Long id) {
    service.cancel(id, user.require(r));
    return ApiResponse.ok();
  }

  @GetMapping("/admin/premium-introductions")
  ApiResponse<?> adminList(HttpServletRequest r) {
    admin.requireActiveAdmin(r);
    return ApiResponse.ok(service.list(null));
  }

  @GetMapping("/admin/premium-introductions/{id}/candidates")
  ApiResponse<?> candidates(HttpServletRequest r, @PathVariable Long id) {
    admin.requireActiveAdmin(r);
    return ApiResponse.ok(service.candidates(id));
  }

  @PatchMapping("/admin/premium-introductions/{id}")
  ApiResponse<?> update(
      HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Update input) {
    service.update(
        id,
        input.status(),
        input.note(),
        input.counterpartUserId(),
        admin.requireActiveAdmin(r).getId());
    return ApiResponse.ok();
  }

  record Update(@NotBlank String status, @Size(max = 1000) String note, Long counterpartUserId) {}
}
