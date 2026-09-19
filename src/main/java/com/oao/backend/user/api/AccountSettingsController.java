package com.oao.backend.user.api;

import com.oao.backend.auth.CurrentUser;
import com.oao.backend.common.*;
import com.oao.backend.user.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountSettingsController {
  private final CurrentUser current;
  private final AccountSettingsService settings;
  private final VerificationReviewService review;

  public AccountSettingsController(
      CurrentUser current, AccountSettingsService settings, VerificationReviewService review) {
    this.current = current;
    this.settings = settings;
    this.review = review;
  }

  @GetMapping("/me/settings")
  ApiResponse<?> get(HttpServletRequest req) {
    return ApiResponse.ok(settings.settings(current.require(req)));
  }

  @PutMapping("/me/settings")
  ApiResponse<?> put(HttpServletRequest req, @Valid @RequestBody Settings input) {
    Long id = current.require(req);
    settings.update(
        id,
        input.matchingEnabled(),
        input.datingStyle(),
        input.keywords(),
        input.alimtalkEnabled(),
        input.messageNotifications());
    return ApiResponse.ok(settings.settings(id));
  }

  @DeleteMapping("/me")
  ApiResponse<?> delete(HttpServletRequest req, @RequestBody Map<String, String> input) {
    if (!"회원탈퇴".equals(input.get("confirmation")))
      throw new BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, "회원탈퇴를 입력해주세요.");
    Long id = current.require(req);
    settings.withdraw(id);
    if (req.getSession(false) != null) req.getSession(false).invalidate();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    return ApiResponse.ok();
  }

  record Settings(
      boolean matchingEnabled,
      @Size(max = 1000) String datingStyle,
      @Size(max = 3) List<@NotBlank @Size(max = 30) String> keywords,
      boolean alimtalkEnabled,
      boolean messageNotifications) {}
}
