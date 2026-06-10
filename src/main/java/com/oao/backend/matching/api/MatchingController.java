package com.oao.backend.matching.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.matching.service.MatchDecisionService;
import com.oao.backend.matching.service.MatchDecisionService.MatchDecisionResult;
import com.oao.backend.matching.service.MatchReadService;
import com.oao.backend.matching.service.MatchReadService.MatchView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matches")
public class MatchingController {

	private final MatchDecisionService matchDecisionService;
	private final MatchReadService matchReadService;

	public MatchingController(MatchDecisionService matchDecisionService, MatchReadService matchReadService) {
		this.matchDecisionService = matchDecisionService;
		this.matchReadService = matchReadService;
	}

	@GetMapping
	ApiResponse<List<MatchView>> matches(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(matchReadService.findPendingMatches(resolveUserId(principal, headerUserId)));
	}

	@GetMapping("/completed")
	ApiResponse<List<MatchView>> completedMatches(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(matchReadService.findCompletedMatches(resolveUserId(principal, headerUserId)));
	}

	@GetMapping("/{matchId}")
	ApiResponse<MatchView> match(
		@PathVariable Long matchId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(matchReadService.findMatch(matchId, resolveUserId(principal, headerUserId)));
	}

	@PostMapping("/{matchId}/accept")
	ApiResponse<MatchDecisionResult> accept(
		@PathVariable Long matchId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(matchDecisionService.accept(matchId, resolveUserId(principal, headerUserId)));
	}

	@PostMapping("/{matchId}/reject")
	ApiResponse<MatchDecisionResult> reject(
		@PathVariable Long matchId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(matchDecisionService.reject(matchId, resolveUserId(principal, headerUserId)));
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
}
