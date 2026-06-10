package com.oao.backend.dev.api;

import com.oao.backend.admin.service.AdminUserDataPurgeService.AdminUserPurgeResult;
import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.dev.service.DevAdminToolService;
import com.oao.backend.dev.service.DevAdminToolService.DevAdminPromotionResult;
import com.oao.backend.dev.service.DevTestUserService;
import com.oao.backend.dev.service.DevTestUserService.DevSeedResult;
import com.oao.backend.dev.service.DevTestUserService.DevTestUserView;
import com.oao.backend.dev.service.DevToolGuardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dev/me")
public class DevToolController {

	private final DevToolGuardService devToolGuardService;
	private final DevAdminToolService devAdminToolService;
	private final DevTestUserService devTestUserService;

	public DevToolController(
		DevToolGuardService devToolGuardService,
		DevAdminToolService devAdminToolService,
		DevTestUserService devTestUserService
	) {
		this.devToolGuardService = devToolGuardService;
		this.devAdminToolService = devAdminToolService;
		this.devTestUserService = devTestUserService;
	}

	@GetMapping("/test-users")
	ApiResponse<List<DevTestUserView>> testUsers(
		@RequestHeader(value = "X-Dev-Secret", required = false) String secret
	) {
		devToolGuardService.requireSecret(secret);
		return ApiResponse.ok(devTestUserService.findTestUsers());
	}

	@PostMapping("/test-users/seed")
	ApiResponse<DevSeedResult> seedTestUsers(
		@RequestHeader(value = "X-Dev-Secret", required = false) String secret
	) {
		devToolGuardService.requireSecret(secret);
		return ApiResponse.ok(devTestUserService.seedTestUsers());
	}

	@PostMapping("/admin")
	ApiResponse<DevAdminPromotionResult> promoteCurrentUser(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-Dev-Secret", required = false) String secret
	) {
		KakaoPrincipal currentPrincipal = devToolGuardService.requireAccess(principal, secret);
		return ApiResponse.ok(devAdminToolService.promoteCurrentUser(currentPrincipal));
	}

	@DeleteMapping("/test-data")
	ApiResponse<AdminUserPurgeResult> purgeCurrentUser(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-Dev-Secret", required = false) String secret,
		HttpServletRequest request
	) {
		KakaoPrincipal currentPrincipal = devToolGuardService.requireAccess(principal, secret);
		AdminUserPurgeResult result = devAdminToolService.purgeCurrentUser(currentPrincipal);
		SecurityContextHolder.clearContext();
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		return ApiResponse.ok(result);
	}
}
