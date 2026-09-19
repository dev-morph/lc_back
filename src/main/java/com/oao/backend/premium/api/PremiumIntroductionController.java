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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/premium-introductions")
public class PremiumIntroductionController {

  private final PremiumIntroductionService premiumIntroductionService;

  @org.springframework.beans.factory.annotation.Autowired
  private com.oao.backend.auth.CurrentUser current;

  public PremiumIntroductionController(PremiumIntroductionService premiumIntroductionService) {
    this.premiumIntroductionService = premiumIntroductionService;
  }

  @PostMapping
  ApiResponse<PremiumIntroductionResponse> create(
      jakarta.servlet.http.HttpServletRequest servletRequest,
      @Valid @RequestBody CreatePremiumIntroductionRequest request) {
    PremiumIntroRequest premiumRequest =
        premiumIntroductionService.create(current.require(servletRequest), request.toCommand());
    return ApiResponse.ok(new PremiumIntroductionResponse(premiumRequest.getId()));
  }

  record CreatePremiumIntroductionRequest(
      @jakarta.validation.constraints.NotNull @Min(19) @Max(99) Integer minAge,
      @jakarta.validation.constraints.NotNull @Min(19) @Max(99) Integer maxAge,
      @jakarta.validation.constraints.NotNull @Min(120) @Max(230) Integer minHeightCm,
      @jakarta.validation.constraints.NotNull @Min(120) @Max(230) Integer maxHeightCm,
      @Min(0) @Max(100) Integer appearanceWeight,
      @Min(0) @Max(100) Integer specWeight,
      @jakarta.validation.constraints.Size(max = 2000) String appearancePreferenceText,
      @jakarta.validation.constraints.Size(max = 1000) String preferredJobGroups,
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 2000)
          String importantPointText,
      List<Long> keywordIds) {

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
          keywordIds == null ? List.of() : keywordIds);
    }
  }

  record PremiumIntroductionResponse(Long requestId) {}
}
