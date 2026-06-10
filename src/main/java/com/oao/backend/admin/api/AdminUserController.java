package com.oao.backend.admin.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.admin.service.AdminUserDataPurgeService;
import com.oao.backend.admin.service.AdminUserDataPurgeService.AdminUserPurgeResult;
import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.heart.service.AdminHeartAdjustmentService;
import com.oao.backend.heart.service.AdminHeartAdjustmentService.AdminHeartAdjustmentCommand;
import com.oao.backend.heart.service.AdminHeartAdjustmentService.AdminHeartAdjustmentType;
import com.oao.backend.heart.service.AdminHeartAdjustmentService.AdminHeartView;
import com.oao.backend.user.domain.AdminUser;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserAccount.Gender;
import com.oao.backend.user.domain.UserAccount.MemberGrade;
import com.oao.backend.user.service.UserApprovalService;
import com.oao.backend.user.service.UserApprovalService.AdminUserCreateCommand;
import com.oao.backend.user.service.UserApprovalService.AdminUserDetailView;
import com.oao.backend.user.service.UserApprovalService.AdminUserSearchCommand;
import com.oao.backend.user.service.UserApprovalService.AdminUserSearchView;
import com.oao.backend.user.service.UserApprovalService.AdminUserUpdateCommand;
import com.oao.backend.user.service.UserApprovalService.PendingApprovalUserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

	private final UserApprovalService userApprovalService;
	private final AdminAccessService adminAccessService;
	private final AdminHeartAdjustmentService adminHeartAdjustmentService;
	private final AdminUserDataPurgeService adminUserDataPurgeService;

	public AdminUserController(
		UserApprovalService userApprovalService,
		AdminAccessService adminAccessService,
		AdminHeartAdjustmentService adminHeartAdjustmentService,
		AdminUserDataPurgeService adminUserDataPurgeService
	) {
		this.userApprovalService = userApprovalService;
		this.adminAccessService = adminAccessService;
		this.adminHeartAdjustmentService = adminHeartAdjustmentService;
		this.adminUserDataPurgeService = adminUserDataPurgeService;
	}

	@GetMapping("/me")
	ApiResponse<AdminMeResponse> me(@AuthenticationPrincipal KakaoPrincipal principal) {
		AdminUser admin = adminAccessService.findActiveAdminOrNull(principal);
		return ApiResponse.ok(AdminMeResponse.from(admin));
	}

	@GetMapping("/pending-approval")
	ApiResponse<List<PendingApprovalUserResponse>> pendingApprovalUsers(
		@AuthenticationPrincipal KakaoPrincipal principal
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(userApprovalService.findPendingUserViews().stream()
			.map(PendingApprovalUserResponse::from)
			.toList());
	}

	@GetMapping
	ApiResponse<List<AdminUserSearchResponse>> searchUsers(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestParam(value = "gender", required = false) String gender,
		@RequestParam(value = "minAge", required = false) Integer minAge,
		@RequestParam(value = "maxAge", required = false) Integer maxAge,
		@RequestParam(value = "joinedFrom", required = false) LocalDate joinedFrom,
		@RequestParam(value = "joinedTo", required = false) LocalDate joinedTo,
		@RequestParam(value = "grade", required = false) String grade,
		@RequestParam(value = "name", required = false) String name,
		@RequestParam(value = "status", required = false) String status,
		@RequestParam(value = "approvalStatus", required = false) String approvalStatus
	) {
		adminAccessService.requireActiveAdmin(principal);
		AdminUserSearchCommand command = new AdminUserSearchCommand(
			blankToNull(gender),
			minAge,
			maxAge,
			joinedFrom,
			joinedTo,
			blankToNull(grade),
			blankToNull(name),
			blankToNull(status),
			blankToNull(approvalStatus)
		);
		return ApiResponse.ok(userApprovalService.searchUsers(command).stream()
			.map(AdminUserSearchResponse::from)
			.toList());
	}

	@PostMapping("/{userId}/approve")
	ApiResponse<UserSummaryResponse> approve(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId,
		@Valid @RequestBody ApproveUserRequest request
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		UserAccount user = userApprovalService.approve(userId, request.grade(), admin.getId(), request.reason());
		return ApiResponse.ok(UserSummaryResponse.from(user));
	}

	@GetMapping("/{userId}")
	ApiResponse<AdminUserDetailResponse> userDetail(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(AdminUserDetailResponse.from(userApprovalService.findAdminUserDetail(userId)));
	}

	@GetMapping("/{userId}/hearts")
	ApiResponse<AdminHeartView> userHearts(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminHeartAdjustmentService.findHearts(userId));
	}

	@PostMapping("/{userId}/hearts/adjust")
	ApiResponse<AdminHeartView> adjustUserHearts(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId,
		@Valid @RequestBody AdjustUserHeartsRequest request
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminHeartAdjustmentService.adjust(userId, request.toCommand(), admin.getId()));
	}

	@PutMapping("/{userId}")
	ApiResponse<AdminUserDetailResponse> updateUser(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId,
		@Valid @RequestBody AdminUserUpdateRequest request
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(AdminUserDetailResponse.from(userApprovalService.updateAdminUser(userId, request.toCommand())));
	}

	@PostMapping("/{userId}/suspend")
	ApiResponse<AdminUserSearchResponse> suspendUser(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(AdminUserSearchResponse.from(userApprovalService.suspendAdminUser(userId)));
	}

	@DeleteMapping("/{userId}")
	ApiResponse<AdminUserSearchResponse> deleteUser(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(AdminUserSearchResponse.from(userApprovalService.deleteAdminUser(userId)));
	}

	@DeleteMapping("/{userId}/test-data")
	ApiResponse<AdminUserPurgeResult> purgeTestUserData(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminUserDataPurgeService.purgeTestUserData(userId, admin));
	}

	@PostMapping
	ApiResponse<AdminUserSearchResponse> createUser(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestParam("name") String name,
		@RequestParam("birthDate") LocalDate birthDate,
		@RequestParam("gender") Gender gender,
		@RequestParam("grade") MemberGrade grade,
		@RequestParam("job") String job,
		@RequestParam("heightCm") Integer heightCm,
		@RequestParam("activityRegion") String activityRegion,
		@RequestParam("mbti") String mbti,
		@RequestParam("education") String education,
		@RequestParam("smokingStatus") String smokingStatus,
		@RequestParam("drinkingStatus") String drinkingStatus,
		@RequestParam("religion") String religion,
		@RequestParam("intro") String intro,
		@RequestParam("hobbies") List<String> hobbies,
		@RequestParam(value = "photos", required = false) List<MultipartFile> photos,
		@RequestParam(value = "photo", required = false) MultipartFile photo
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		AdminUserCreateCommand command = new AdminUserCreateCommand(
			name,
			birthDate,
			gender,
			grade,
			job,
			heightCm,
			activityRegion,
			mbti,
			education,
			smokingStatus,
			drinkingStatus,
			religion,
			intro,
			hobbies
		);
		return ApiResponse.ok(AdminUserSearchResponse.from(userApprovalService.createAdminUser(command, photos, photo, admin.getId())));
	}

	@PostMapping("/{userId}/reject")
	ApiResponse<UserSummaryResponse> reject(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(UserSummaryResponse.from(userApprovalService.reject(userId)));
	}

	@PatchMapping("/{userId}/grade")
	ApiResponse<UserSummaryResponse> changeGrade(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long userId,
		@Valid @RequestBody ChangeGradeRequest request
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		UserAccount user = userApprovalService.changeGrade(userId, request.grade(), admin.getId(), request.reason());
		return ApiResponse.ok(UserSummaryResponse.from(user));
	}

	record AdminMeResponse(boolean admin, Long adminId, String name, String role) {

		static AdminMeResponse from(AdminUser admin) {
			if (admin == null) {
				return new AdminMeResponse(false, null, null, null);
			}
			return new AdminMeResponse(true, admin.getId(), admin.getName(), admin.getRole());
		}
	}

	record ApproveUserRequest(@NotNull MemberGrade grade, String reason) {
	}

	record ChangeGradeRequest(@NotNull MemberGrade grade, String reason) {
	}

	record AdjustUserHeartsRequest(@NotNull AdminHeartAdjustmentType type, @NotNull Integer amount, String reason) {

		AdminHeartAdjustmentCommand toCommand() {
			return new AdminHeartAdjustmentCommand(type, amount, reason);
		}
	}

	record AdminUserUpdateRequest(
		String name,
		LocalDate birthDate,
		Gender gender,
		MemberGrade grade,
		String job,
		Integer heightCm,
		String activityRegion,
		String mbti,
		String education,
		String smokingStatus,
		String drinkingStatus,
		String religion,
		String intro
	) {

		AdminUserUpdateCommand toCommand() {
			return new AdminUserUpdateCommand(
				name,
				birthDate,
				gender,
				grade,
				job,
				heightCm,
				activityRegion,
				mbti,
				education,
				smokingStatus,
				drinkingStatus,
				religion,
				intro
			);
		}
	}

	record PendingApprovalUserResponse(
		Long userId,
		String email,
		String name,
		LocalDate birthDate,
		String job,
		Integer heightCm,
		String activityRegion,
		String mbti,
		String education,
		String smokingStatus,
		String drinkingStatus,
		String religion,
		String intro,
		String photoUrl,
		String approvalStatus,
		String grade
	) {

		static PendingApprovalUserResponse from(PendingApprovalUserView view) {
			return new PendingApprovalUserResponse(
				view.userId(),
				view.email(),
				view.name(),
				view.birthDate(),
				view.job(),
				view.heightCm(),
				view.activityRegion(),
				view.mbti(),
				view.education(),
				view.smokingStatus(),
				view.drinkingStatus(),
				view.religion(),
				view.intro(),
				view.photoUrl(),
				view.approvalStatus().name(),
				view.grade() == null ? null : view.grade().name()
			);
		}
	}

	record AdminUserSearchResponse(
		Long userId,
		String email,
		String name,
		LocalDate birthDate,
		String gender,
		LocalDate joinedAt,
		String status,
		String approvalStatus,
		String grade,
		String job,
		String activityRegion
	) {

		static AdminUserSearchResponse from(AdminUserSearchView view) {
			return new AdminUserSearchResponse(
				view.userId(),
				view.email(),
				view.name(),
				view.birthDate(),
				view.gender(),
				view.joinedAt(),
				view.status(),
				view.approvalStatus(),
				view.grade(),
				view.job(),
				view.activityRegion()
			);
		}
	}

	record AdminUserDetailResponse(
		Long userId,
		String email,
		String name,
		LocalDate birthDate,
		String gender,
		String status,
		String approvalStatus,
		String grade,
		String job,
		Integer heightCm,
		String activityRegion,
		String mbti,
		String education,
		String smokingStatus,
		String drinkingStatus,
		String religion,
		String intro,
		String photoUrl
	) {

		static AdminUserDetailResponse from(AdminUserDetailView view) {
			return new AdminUserDetailResponse(
				view.userId(),
				view.email(),
				view.name(),
				view.birthDate(),
				view.gender(),
				view.status(),
				view.approvalStatus(),
				view.grade(),
				view.job(),
				view.heightCm(),
				view.activityRegion(),
				view.mbti(),
				view.education(),
				view.smokingStatus(),
				view.drinkingStatus(),
				view.religion(),
				view.intro(),
				view.photoUrl()
			);
		}
	}

	record UserSummaryResponse(Long userId, String approvalStatus, String grade) {

		static UserSummaryResponse from(UserAccount user) {
			return new UserSummaryResponse(
				user.getId(),
				user.getApprovalStatus().name(),
				user.getGrade() == null ? null : user.getGrade().name()
			);
		}
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
