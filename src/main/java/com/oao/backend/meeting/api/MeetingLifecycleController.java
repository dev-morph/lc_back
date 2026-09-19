package com.oao.backend.meeting.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.auth.CurrentUser;
import com.oao.backend.common.*;
import com.oao.backend.meeting.service.MeetingLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.*;

@RestController
public class MeetingLifecycleController {
  private final CurrentUser user;
  private final MeetingLifecycleService service;
  private final AdminAccessService admin;

  public MeetingLifecycleController(
      CurrentUser user, MeetingLifecycleService service, AdminAccessService admin) {
    this.user = user;
    this.service = service;
    this.admin = admin;
  }

  @GetMapping("/meetings/{id}/my-application")
  ApiResponse<?> application(HttpServletRequest r, @PathVariable Long id) {
    return ApiResponse.ok(service.application(id, user.require(r)));
  }

  @DeleteMapping("/meetings/{id}/my-application")
  ApiResponse<?> cancel(HttpServletRequest r, @PathVariable Long id) {
    service.cancel(id, user.require(r));
    return ApiResponse.ok();
  }

  @PutMapping("/admin/meetings/{id}")
  ApiResponse<?> update(
      HttpServletRequest r, @PathVariable Long id, @Valid @RequestBody Update input) {
    admin.requireActiveAdmin(r);
    service.update(
        id,
        input.title(),
        input.description(),
        input.eventDateTime(),
        input.priceAmount(),
        input.capacity(),
        input.status());
    return ApiResponse.ok();
  }

  @DeleteMapping("/admin/meetings/{id}")
  ApiResponse<?> delete(HttpServletRequest r, @PathVariable Long id) {
    admin.requireActiveAdmin(r);
    service.delete(id);
    return ApiResponse.ok();
  }

  record Update(
      @NotBlank @Size(max = 120) String title,
      @NotBlank @Size(max = 20000) String description,
      @NotNull LocalDateTime eventDateTime,
      @Min(0) int priceAmount,
      @Min(1) @Max(10000) int capacity,
      @NotBlank String status) {}
}
