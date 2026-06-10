package com.oao.backend.auth;

import com.oao.backend.common.ApiResponse;
import com.oao.backend.dev.service.DevToolGuardService;
import com.oao.backend.user.domain.OAuthAccount;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.OAuthAccountRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final OAuthAccountRepository oAuthAccountRepository;
	private final DevToolGuardService devToolGuardService;

	public AuthController(
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		OAuthAccountRepository oAuthAccountRepository,
		DevToolGuardService devToolGuardService
	) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.oAuthAccountRepository = oAuthAccountRepository;
		this.devToolGuardService = devToolGuardService;
	}

	@GetMapping("/oauth/kakao/authorize")
	void kakaoAuthorize(HttpServletResponse response) throws IOException {
		response.sendRedirect("/oauth2/authorization/kakao");
	}

	@GetMapping("/auth/me")
	ApiResponse<AuthMeResponse> me(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long devUserId,
		@RequestHeader(value = "X-Dev-Secret", required = false) String devSecret
	) {
		if (devUserId != null) {
			devToolGuardService.requireSecret(devSecret);
			return userAccountRepository.findById(devUserId)
				.map(user -> ApiResponse.ok(AuthMeResponse.from(user, email(user.getId()), phoneVerified(user.getId()))))
				.orElseGet(() -> ApiResponse.ok(AuthMeResponse.anonymous()));
		}

		if (principal == null || !userAccountRepository.existsById(principal.getUserId())) {
			return ApiResponse.ok(AuthMeResponse.anonymous());
		}
		return ApiResponse.ok(AuthMeResponse.from(principal, phoneVerified(principal.getUserId())));
	}

	@RequestMapping("/auth/logout")
	ApiResponse<Void> logoutFallback() {
		return ApiResponse.ok();
	}

	record AuthMeResponse(
		boolean authenticated,
		Long userId,
		String approvalStatus,
		String grade,
		String email,
		String nickname,
		boolean phoneVerified
	) {

		static AuthMeResponse anonymous() {
			return new AuthMeResponse(false, null, null, null, null, null, false);
		}

		static AuthMeResponse from(KakaoPrincipal principal, boolean phoneVerified) {
			return new AuthMeResponse(
				true,
				principal.getUserId(),
				principal.getApprovalStatus().name(),
				principal.getGrade() == null ? null : principal.getGrade().name(),
				principal.getEmail(),
				principal.getNickname(),
				phoneVerified
			);
		}

		static AuthMeResponse from(UserAccount user, String email, boolean phoneVerified) {
			return new AuthMeResponse(
				true,
				user.getId(),
				user.getApprovalStatus().name(),
				user.getGrade() == null ? null : user.getGrade().name(),
				email,
				user.getName(),
				phoneVerified
			);
		}
	}

	private String email(Long userId) {
		return oAuthAccountRepository.findFirstByUserId(userId)
			.map(OAuthAccount::getEmail)
			.orElse(null);
	}

	private boolean phoneVerified(Long userId) {
		return userProfileRepository.findByUserId(userId)
			.map(AuthController::isPhoneVerified)
			.orElse(false);
	}

	private static boolean isPhoneVerified(UserProfile profile) {
		return profile.getPhoneNumber() != null && profile.getPhoneVerifiedAt() != null;
	}
}
