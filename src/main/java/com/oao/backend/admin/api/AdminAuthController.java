package com.oao.backend.admin.api;

import com.oao.backend.admin.service.AdminAuthService;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.user.domain.AdminUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

	public AdminAuthController(AdminAuthService adminAuthService) {
		this.adminAuthService = adminAuthService;
	}

	@PostMapping("/login")
	ApiResponse<AdminMeResponse> login(
		@Valid @RequestBody AdminLoginRequest request,
		HttpServletRequest servletRequest
	) {
		AdminUser admin = adminAuthService.login(request.email(), request.password(), servletRequest);
		return ApiResponse.ok(AdminMeResponse.from(admin));
	}

	@PostMapping("/logout")
	ApiResponse<Void> logout(HttpServletRequest request) {
		adminAuthService.logout(request);
		return ApiResponse.ok(null);
	}

	@GetMapping("/me")
	ApiResponse<AdminMeResponse> me(HttpServletRequest request) {
		return ApiResponse.ok(AdminMeResponse.from(adminAuthService.findCurrentAdminOrNull(request)));
	}

	record AdminLoginRequest(@NotBlank @Email String email, @NotBlank String password) {
	}
}
