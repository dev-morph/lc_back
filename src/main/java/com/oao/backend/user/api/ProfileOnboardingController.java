package com.oao.backend.user.api;

import com.oao.backend.auth.CurrentUser;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.user.service.ProfileOnboardingService;
import com.oao.backend.user.service.ProfileOnboardingService.OnboardingStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/onboarding")
public class ProfileOnboardingController {

  private final CurrentUser currentUser;
  private final ProfileOnboardingService onboarding;

  public ProfileOnboardingController(
      CurrentUser currentUser, ProfileOnboardingService onboarding) {
    this.currentUser = currentUser;
    this.onboarding = onboarding;
  }

  @GetMapping("/status")
  ApiResponse<OnboardingStatus> status(HttpServletRequest request) {
    return ApiResponse.ok(onboarding.status(currentUser.require(request)));
  }

  @PostMapping("/complete")
  ApiResponse<OnboardingStatus> complete(HttpServletRequest request) {
    return ApiResponse.ok(onboarding.complete(currentUser.require(request)));
  }
}
