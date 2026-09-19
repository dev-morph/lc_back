package com.oao.backend.user.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.auth.*;
import com.oao.backend.common.*;
import com.oao.backend.user.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModerationController {
  private final CurrentUser current;
  private final ModerationService service;
  private final AdminAccessService admin;
  private final RequestRateLimiter rate;

  public ModerationController(
      CurrentUser current,
      ModerationService service,
      AdminAccessService admin,
      RequestRateLimiter rate) {
    this.current = current;
    this.service = service;
    this.admin = admin;
    this.rate = rate;
  }

  @GetMapping("/me/blocks")
  ApiResponse<?> blocks(HttpServletRequest r) {
    return ApiResponse.ok(service.blocks(current.require(r)));
  }

  @PostMapping("/blocks")
  ApiResponse<?> block(HttpServletRequest r, @Valid @RequestBody Target t) {
    service.block(current.require(r), t.userId());
    return ApiResponse.ok();
  }

  @DeleteMapping("/blocks/{id}")
  ApiResponse<?> unblock(HttpServletRequest r, @PathVariable Long id) {
    service.unblock(current.require(r), id);
    return ApiResponse.ok();
  }

  @PostMapping("/reports")
  ApiResponse<?> report(HttpServletRequest r, @Valid @RequestBody Report t) {
    Long user = current.require(r);
    rate.check("report:" + user, 10, 3600);
    service.report(user, t.userId(), t.reason(), t.targetType(), t.targetId());
    return ApiResponse.ok();
  }

  @GetMapping("/admin/reports")
  ApiResponse<?> reports(HttpServletRequest r) {
    admin.requireActiveAdmin(r);
    return ApiResponse.ok(service.reports());
  }

  @PatchMapping("/admin/reports/{id}")
  ApiResponse<?> resolve(
      HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Resolution t) {
    Long a = admin.requireActiveAdmin(r).getId();
    service.resolve(id, t.status(), t.note(), t.suspend(), a);
    return ApiResponse.ok();
  }

  record Target(@NotNull Long userId) {}

  record Report(
      @NotNull Long userId,
      @NotBlank @Size(min = 5, max = 1000) String reason,
      @NotBlank String targetType,
      Long targetId) {}

  record Resolution(@NotBlank String status, @Size(max = 1000) String note, boolean suspend) {}
}
