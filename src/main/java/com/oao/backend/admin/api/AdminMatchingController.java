package com.oao.backend.admin.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.heart.service.InstantIntroductionConfigService;
import com.oao.backend.heart.service.InstantIntroductionConfigService.InstantIntroductionConfigUpdateCommand;
import com.oao.backend.heart.service.InstantIntroductionConfigService.InstantIntroductionConfigView;
import com.oao.backend.matching.service.AutoMatchingService;
import com.oao.backend.matching.service.AutoMatchingService.AutoMatchingResult;
import com.oao.backend.matching.service.AdminMatchingOperationsService;
import com.oao.backend.matching.service.AdminMatchingOperationsService.ManualMatchCommand;
import com.oao.backend.matching.service.AdminMatchingOperationsService.ManualMatchResult;
import com.oao.backend.matching.service.AdminMatchingOperationsService.ManualMatchScoreCommand;
import com.oao.backend.matching.service.AdminMatchingOperationsService.ManualMatchScorePreview;
import com.oao.backend.matching.service.AdminMatchingOperationsService.MatchingOperationsView;
import com.oao.backend.matching.service.MatchingScheduleService;
import com.oao.backend.matching.service.MatchingScheduleService.MatchingScheduleUpdateCommand;
import com.oao.backend.matching.service.MatchingScheduleService.MatchingScheduleView;
import com.oao.backend.matching.service.MatchingScoreConfigService;
import com.oao.backend.matching.service.MatchingScoreConfigService.MatchingScoreConfigUpdateCommand;
import com.oao.backend.matching.service.MatchingScoreConfigService.MatchingScoreConfigView;
import com.oao.backend.user.domain.AdminUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/matching")
public class AdminMatchingController {

	private final AdminAccessService adminAccessService;
	private final AutoMatchingService autoMatchingService;
	private final AdminMatchingOperationsService adminMatchingOperationsService;
	private final MatchingScheduleService matchingScheduleService;
	private final InstantIntroductionConfigService instantIntroductionConfigService;
	private final MatchingScoreConfigService matchingScoreConfigService;

	public AdminMatchingController(
		AdminAccessService adminAccessService,
		AutoMatchingService autoMatchingService,
		AdminMatchingOperationsService adminMatchingOperationsService,
		MatchingScheduleService matchingScheduleService,
		InstantIntroductionConfigService instantIntroductionConfigService,
		MatchingScoreConfigService matchingScoreConfigService
	) {
		this.adminAccessService = adminAccessService;
		this.autoMatchingService = autoMatchingService;
		this.adminMatchingOperationsService = adminMatchingOperationsService;
		this.matchingScheduleService = matchingScheduleService;
		this.instantIntroductionConfigService = instantIntroductionConfigService;
		this.matchingScoreConfigService = matchingScoreConfigService;
	}

	@PostMapping("/auto/run")
	ApiResponse<AutoMatchingResult> runAutoMatching(@AuthenticationPrincipal KakaoPrincipal principal) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(autoMatchingService.runAutoMatching());
	}

	@GetMapping("/schedule")
	ApiResponse<MatchingScheduleView> schedule(@AuthenticationPrincipal KakaoPrincipal principal) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(matchingScheduleService.findSchedule());
	}

	@GetMapping("/instant-introduction-config")
	ApiResponse<InstantIntroductionConfigView> instantIntroductionConfig(@AuthenticationPrincipal KakaoPrincipal principal) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(instantIntroductionConfigService.findConfig());
	}

	@GetMapping("/score-config")
	ApiResponse<MatchingScoreConfigView> matchingScoreConfig(@AuthenticationPrincipal KakaoPrincipal principal) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(matchingScoreConfigService.findConfig());
	}

	@GetMapping("/operations")
	ApiResponse<MatchingOperationsView> operations(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestParam("date") LocalDate date
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminMatchingOperationsService.findOperations(date));
	}

	@PostMapping("/manual")
	ApiResponse<ManualMatchResult> createManualMatch(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@Valid @RequestBody ManualMatchRequest request
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminMatchingOperationsService.createManualMatch(request.toCommand(), admin.getId()));
	}

	@PostMapping("/manual/score-preview")
	ApiResponse<ManualMatchScorePreview> manualMatchScorePreview(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@Valid @RequestBody ManualMatchScoreRequest request
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminMatchingOperationsService.previewManualMatchScore(request.toCommand()));
	}

	@PutMapping("/schedule")
	ApiResponse<MatchingScheduleView> updateSchedule(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@Valid @RequestBody UpdateScheduleRequest request
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(matchingScheduleService.updateSchedule(request.toCommand(), admin.getId()));
	}

	@PutMapping("/instant-introduction-config")
	ApiResponse<InstantIntroductionConfigView> updateInstantIntroductionConfig(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@Valid @RequestBody UpdateInstantIntroductionConfigRequest request
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(instantIntroductionConfigService.updateConfig(request.toCommand(), admin.getId()));
	}

	@PutMapping("/score-config")
	ApiResponse<MatchingScoreConfigView> updateMatchingScoreConfig(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@Valid @RequestBody UpdateMatchingScoreConfigRequest request
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(matchingScoreConfigService.updateConfig(request.toCommand(), admin.getId()));
	}

	record UpdateScheduleRequest(
		boolean enabled,
		String dailyTime,
		List<String> dailyTimes,
		Integer intervalHours,
		String cronExpression,
		String timezone
	) {

		MatchingScheduleUpdateCommand toCommand() {
			return new MatchingScheduleUpdateCommand(
				enabled,
				dailyTime == null || dailyTime.isBlank() ? null : LocalTime.parse(dailyTime),
				dailyTimes == null ? List.of() : dailyTimes.stream().map(LocalTime::parse).toList(),
				intervalHours,
				cronExpression,
				timezone
			);
		}
	}

	record ManualMatchRequest(@NotNull Long userAId, @NotNull Long userBId, String reason) {

		ManualMatchCommand toCommand() {
			return new ManualMatchCommand(userAId, userBId, reason);
		}
	}

	record ManualMatchScoreRequest(@NotNull Long userAId, @NotNull Long userBId) {

		ManualMatchScoreCommand toCommand() {
			return new ManualMatchScoreCommand(userAId, userBId);
		}
	}

	record UpdateInstantIntroductionConfigRequest(
		@NotNull Integer firstUsageCost,
		@NotNull Integer midTierEndCount,
		@NotNull Integer midTierCost,
		@NotNull Integer highTierCost,
		@NotNull Integer usageWindowHours
	) {

		InstantIntroductionConfigUpdateCommand toCommand() {
			return new InstantIntroductionConfigUpdateCommand(
				firstUsageCost,
				midTierEndCount,
				midTierCost,
				highTierCost,
				usageWindowHours
			);
		}
	}

	record UpdateMatchingScoreConfigRequest(
		@NotNull Integer hobbyPointPerMatch,
		@NotNull Integer hobbyMaxPoint,
		@NotNull Integer sameSmokingPoint,
		@NotNull Integer sameDrinkingPoint,
		@NotNull Integer sameReligionPoint,
		@NotNull Integer sameGradePoint,
		@NotNull Integer adjacentGradePoint,
		boolean allowPreviousAutoMatch
	) {

		MatchingScoreConfigUpdateCommand toCommand() {
			return new MatchingScoreConfigUpdateCommand(
				hobbyPointPerMatch,
				hobbyMaxPoint,
				sameSmokingPoint,
				sameDrinkingPoint,
				sameReligionPoint,
				sameGradePoint,
				adjacentGradePoint,
				allowPreviousAutoMatch
			);
		}
	}
}
