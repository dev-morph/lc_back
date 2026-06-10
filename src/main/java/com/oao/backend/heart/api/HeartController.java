package com.oao.backend.heart.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.heart.domain.HeartTransaction;
import com.oao.backend.heart.repository.HeartTransactionRepository;
import com.oao.backend.heart.repository.HeartWalletRepository;
import com.oao.backend.heart.service.HeartProductService;
import com.oao.backend.heart.service.HeartProductService.HeartProductView;
import com.oao.backend.heart.service.HeartPurchaseService;
import com.oao.backend.heart.service.HeartPurchaseService.HeartPurchaseResult;
import com.oao.backend.heart.service.InstantIntroductionService;
import com.oao.backend.heart.service.InstantIntroductionService.InstantIntroductionCost;
import com.oao.backend.heart.service.InstantIntroductionService.InstantIntroductionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HeartController {

	private final InstantIntroductionService instantIntroductionService;
	private final HeartProductService heartProductService;
	private final HeartPurchaseService heartPurchaseService;
	private final HeartWalletRepository heartWalletRepository;
	private final HeartTransactionRepository heartTransactionRepository;

	public HeartController(
		InstantIntroductionService instantIntroductionService,
		HeartProductService heartProductService,
		HeartPurchaseService heartPurchaseService,
		HeartWalletRepository heartWalletRepository,
		HeartTransactionRepository heartTransactionRepository
	) {
		this.instantIntroductionService = instantIntroductionService;
		this.heartProductService = heartProductService;
		this.heartPurchaseService = heartPurchaseService;
		this.heartWalletRepository = heartWalletRepository;
		this.heartTransactionRepository = heartTransactionRepository;
	}

	@GetMapping("/me/hearts")
	ApiResponse<HeartBalanceResponse> hearts(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		Long userId = resolveUserId(principal, headerUserId);
		int balance = heartWalletRepository.findByUserId(userId)
			.map(wallet -> wallet.getBalance())
			.orElse(0);
		return ApiResponse.ok(new HeartBalanceResponse(userId, balance));
	}

	@GetMapping("/me/hearts/transactions")
	ApiResponse<List<HeartTransaction>> heartTransactions(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		Long userId = resolveUserId(principal, headerUserId);
		return ApiResponse.ok(heartTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId));
	}

	@GetMapping("/me/instant-introduction-cost")
	ApiResponse<InstantIntroductionCost> instantIntroductionCost(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(instantIntroductionService.currentCost(resolveUserId(principal, headerUserId)));
	}

	@PostMapping("/matching/instant-introductions")
	ApiResponse<InstantIntroductionResult> requestInstantIntroduction(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(instantIntroductionService.request(resolveUserId(principal, headerUserId)));
	}

	@GetMapping("/hearts/products")
	ApiResponse<List<HeartProductView>> heartProducts() {
		return ApiResponse.ok(heartProductService.findActiveProducts());
	}

	@PostMapping("/hearts/purchases/mock")
	ApiResponse<HeartPurchaseResult> purchaseMock(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@Valid @RequestBody MockPurchaseRequest request
	) {
		return ApiResponse.ok(heartPurchaseService.purchaseMock(resolveUserId(principal, headerUserId), request.heartProductId()));
	}

	record HeartBalanceResponse(Long userId, int balance) {
	}

	record MockPurchaseRequest(@NotNull Long heartProductId) {
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
