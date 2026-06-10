package com.oao.backend.premium.api;

import com.oao.backend.common.ApiResponse;
import com.oao.backend.premium.domain.PremiumIntroRequest;
import com.oao.backend.premium.service.PremiumIntroductionService;
import com.oao.backend.premium.service.PremiumIntroductionService.CreatePremiumIntroductionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/premium-introductions")
public class PremiumIntroductionController {

	private final PremiumIntroductionService premiumIntroductionService;

	public PremiumIntroductionController(PremiumIntroductionService premiumIntroductionService) {
		this.premiumIntroductionService = premiumIntroductionService;
	}

	@PostMapping
	ApiResponse<PremiumIntroductionResponse> create(
		@RequestHeader("X-User-Id") Long userId,
		@Valid @RequestBody CreatePremiumIntroductionRequest request
	) {
		PremiumIntroRequest premiumRequest = premiumIntroductionService.create(userId, request.toCommand());
		return ApiResponse.ok(new PremiumIntroductionResponse(premiumRequest.getId()));
	}

	record CreatePremiumIntroductionRequest(
		Integer minAge,
		Integer maxAge,
		Integer minHeightCm,
		Integer maxHeightCm,
		@Min(0) @Max(100) Integer appearanceWeight,
		@Min(0) @Max(100) Integer specWeight,
		String appearancePreferenceText,
		String preferredJobGroups,
		String importantPointText,
		List<Long> keywordIds
	) {

		CreatePremiumIntroductionCommand toCommand() {
			return new CreatePremiumIntroductionCommand(
				minAge,
				maxAge,
				minHeightCm,
				maxHeightCm,
				appearanceWeight,
				specWeight,
				appearancePreferenceText,
				preferredJobGroups,
				importantPointText,
				keywordIds == null ? List.of() : keywordIds
			);
		}
	}

	record PremiumIntroductionResponse(Long requestId) {
	}
}
