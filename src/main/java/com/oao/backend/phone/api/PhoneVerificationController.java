package com.oao.backend.phone.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.phone.service.PhoneVerificationService;
import com.oao.backend.phone.service.PhoneVerificationService.ConfirmPhoneVerificationResult;
import com.oao.backend.phone.service.PhoneVerificationService.PhoneVerificationStatusView;
import com.oao.backend.phone.service.PhoneVerificationService.SendPhoneVerificationResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/phone-verification")
public class PhoneVerificationController {

	private final PhoneVerificationService phoneVerificationService;

	public PhoneVerificationController(PhoneVerificationService phoneVerificationService) {
		this.phoneVerificationService = phoneVerificationService;
	}

	@GetMapping("/status")
	ApiResponse<PhoneVerificationStatusView> status(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(phoneVerificationService.status(resolveUserId(principal, headerUserId)));
	}

	@PostMapping("/send")
	ApiResponse<SendPhoneVerificationResult> send(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@Valid @RequestBody SendPhoneVerificationRequest request
	) {
		return ApiResponse.ok(phoneVerificationService.send(resolveUserId(principal, headerUserId), request.phoneNumber()));
	}

	@PostMapping("/confirm")
	ApiResponse<ConfirmPhoneVerificationResult> confirm(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@Valid @RequestBody ConfirmPhoneVerificationRequest request
	) {
		return ApiResponse.ok(phoneVerificationService.confirm(
			resolveUserId(principal, headerUserId),
			request.verificationId(),
			request.code()
		));
	}

	private Long resolveUserId(KakaoPrincipal principal, Long headerUserId) {
		if (principal != null) {
			return principal.getUserId();
		}
		if (headerUserId != null) {
			return headerUserId;
		}
		throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
	}

	record SendPhoneVerificationRequest(@NotBlank String phoneNumber) {
	}

	record ConfirmPhoneVerificationRequest(@NotNull Long verificationId, @NotBlank String code) {
	}
}
