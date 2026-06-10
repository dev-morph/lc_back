package com.oao.backend.admin.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.heart.service.HeartProductService;
import com.oao.backend.heart.service.HeartProductService.HeartProductUpdateCommand;
import com.oao.backend.heart.service.HeartProductService.HeartProductView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings/heart-products")
public class AdminHeartProductController {

	private final AdminAccessService adminAccessService;
	private final HeartProductService heartProductService;

	public AdminHeartProductController(AdminAccessService adminAccessService, HeartProductService heartProductService) {
		this.adminAccessService = adminAccessService;
		this.heartProductService = heartProductService;
	}

	@GetMapping
	ApiResponse<List<HeartProductView>> products(@AuthenticationPrincipal KakaoPrincipal principal) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(heartProductService.findAdminProducts());
	}

	@PutMapping
	ApiResponse<List<HeartProductView>> updateProducts(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@Valid @RequestBody UpdateHeartProductsRequest request
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(heartProductService.updateProducts(request.toCommands()));
	}

	record UpdateHeartProductsRequest(@NotNull List<@Valid HeartProductRequest> products) {

		List<HeartProductUpdateCommand> toCommands() {
			return products.stream()
				.map(product -> new HeartProductUpdateCommand(
					product.id(),
					product.name(),
					product.heartAmount(),
					product.price(),
					product.displayDiscountRate(),
					product.status(),
					product.sortOrder(),
					product.recommended()
				))
				.toList();
		}
	}

	record HeartProductRequest(
		@NotNull Long id,
		@NotBlank String name,
		@Positive int heartAmount,
		@Positive long price,
		@Positive int displayDiscountRate,
		@NotBlank String status,
		int sortOrder,
		boolean recommended
	) {
	}
}
