package com.oao.backend.interest.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.interest.domain.UserInterest.InterestType;
import com.oao.backend.interest.service.UserInterestService;
import com.oao.backend.interest.service.UserInterestService.InterestActionResult;
import com.oao.backend.interest.service.UserInterestService.InterestProfileDetailView;
import com.oao.backend.interest.service.UserInterestService.InterestProfileView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/interests")
public class UserInterestController {

	private final UserInterestService interestService;

	public UserInterestController(UserInterestService interestService) {
		this.interestService = interestService;
	}

	@GetMapping("/received")
	ApiResponse<List<InterestProfileView>> received(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(interestService.received(resolveUserId(principal, headerUserId)));
	}

	@GetMapping("/sent")
	ApiResponse<List<InterestProfileView>> sent(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(interestService.sent(resolveUserId(principal, headerUserId)));
	}

	@GetMapping("/profiles/{profileUserId}")
	ApiResponse<InterestProfileDetailView> profile(
		@PathVariable Long profileUserId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(interestService.profileDetail(resolveUserId(principal, headerUserId), profileUserId));
	}

	@PostMapping
	ApiResponse<InterestActionResult> send(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@Valid @RequestBody SendInterestRequest request
	) {
		InterestType interestType = request.interestType() == null ? InterestType.LIKE : request.interestType();
		return ApiResponse.ok(interestService.send(resolveUserId(principal, headerUserId), request.receiverUserId(), interestType, request.message()));
	}

	@PostMapping("/{interestId}/accept")
	ApiResponse<InterestActionResult> acceptExpress(
		@PathVariable Long interestId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(interestService.acceptExpress(interestId, resolveUserId(principal, headerUserId)));
	}

	@PostMapping("/{interestId}/reject")
	ApiResponse<InterestActionResult> rejectExpress(
		@PathVariable Long interestId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(interestService.rejectExpress(interestId, resolveUserId(principal, headerUserId)));
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

	record SendInterestRequest(@NotNull Long receiverUserId, InterestType interestType, String message) {
	}
}
