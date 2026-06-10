package com.oao.backend.user.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserAccount.Gender;
import com.oao.backend.user.domain.UserVerificationDocument;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserVerificationDocumentRepository;
import com.oao.backend.user.service.ProfileIntroPhotoService;
import com.oao.backend.user.service.ProfileIntroPhotoService.IntroPhotoView;
import com.oao.backend.user.service.UserProfileService;
import com.oao.backend.user.service.UserProfileService.ProfileUpdateCommand;
import com.oao.backend.user.service.UserProfileService.ProfileView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/me")
public class MeController {

	private final UserAccountRepository userAccountRepository;
	private final UserVerificationDocumentRepository documentRepository;
	private final UserProfileService userProfileService;
	private final ProfileIntroPhotoService profileIntroPhotoService;

	public MeController(
		UserAccountRepository userAccountRepository,
		UserVerificationDocumentRepository documentRepository,
		UserProfileService userProfileService,
		ProfileIntroPhotoService profileIntroPhotoService
	) {
		this.userAccountRepository = userAccountRepository;
		this.documentRepository = documentRepository;
		this.userProfileService = userProfileService;
		this.profileIntroPhotoService = profileIntroPhotoService;
	}

	@GetMapping("/approval-status")
	ApiResponse<ApprovalStatusResponse> approvalStatus(@RequestHeader("X-User-Id") Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found."));
		return ApiResponse.ok(ApprovalStatusResponse.from(user));
	}

	@GetMapping("/profile")
	ApiResponse<ProfileResponse> profile(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		Long userId = resolveUserId(principal, headerUserId);
		return ApiResponse.ok(ProfileResponse.from(userProfileService.findProfile(userId)));
	}

	@PutMapping("/profile")
	ApiResponse<ProfileSaveResponse> updateProfile(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@Valid @RequestBody ProfileUpdateRequest request
	) {
		Long userId = resolveUserId(principal, headerUserId);
		ProfileView profile = userProfileService.updateProfile(userId, request.toCommand());
		return ApiResponse.ok(new ProfileSaveResponse(profile.profileCompleted()));
	}

	@GetMapping("/profile/intro-photo")
	ApiResponse<IntroPhotoResponse> introPhoto(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		Long userId = resolveUserId(principal, headerUserId);
		return ApiResponse.ok(IntroPhotoResponse.from(profileIntroPhotoService.findIntroPhoto(userId)));
	}

	@PostMapping("/profile/intro-photo")
	ApiResponse<IntroPhotoSaveResponse> saveIntroPhoto(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@RequestParam("intro") String intro,
		@RequestParam(value = "photos", required = false) List<MultipartFile> photos,
		@RequestParam(value = "photo", required = false) MultipartFile photo
	) {
		Long userId = resolveUserId(principal, headerUserId);
		IntroPhotoView introPhoto = profileIntroPhotoService.saveIntroPhoto(userId, intro, photos, photo);
		return ApiResponse.ok(new IntroPhotoSaveResponse(introPhoto.completed()));
	}

	@PostMapping("/verification-documents")
	ApiResponse<VerificationDocumentResponse> submitVerificationDocument(
		@RequestHeader("X-User-Id") Long userId,
		@Valid @RequestBody SubmitVerificationDocumentRequest request
	) {
		UserVerificationDocument document = documentRepository.save(
			UserVerificationDocument.create(userId, request.documentType(), request.fileUrl())
		);
		return ApiResponse.ok(VerificationDocumentResponse.from(document));
	}

	@DeleteMapping("/verification-documents/{documentId}")
	ApiResponse<Void> deleteVerificationDocument(
		@RequestHeader("X-User-Id") Long userId,
		@PathVariable Long documentId
	) {
		documentRepository.deleteById(documentId);
		return ApiResponse.ok();
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

	record SubmitVerificationDocumentRequest(@NotBlank String documentType, @NotBlank String fileUrl) {
	}

	record ProfileUpdateRequest(
		@NotBlank @Size(min = 2, max = 100) String name,
		@NotNull @PastOrPresent LocalDate birthDate,
		@NotNull Gender gender,
		@NotBlank @Size(min = 2, max = 100) String job,
		@NotNull @Min(120) @Max(230) Integer heightCm,
		@NotBlank @Size(max = 32) String bodyType,
		@NotBlank @Size(max = 32) String smokingStatus,
		@NotBlank @Size(max = 32) String drinkingStatus,
		@NotBlank @Size(max = 64) String religion,
		@NotBlank @Size(max = 8) String mbti,
		@NotBlank @Size(max = 100) String education,
		@NotBlank @Size(max = 64) String activityRegion,
		@NotNull @Size(min = 1, max = 8) List<@NotBlank @Size(max = 64) String> hobbies
	) {

		ProfileUpdateCommand toCommand() {
			return new ProfileUpdateCommand(
				name,
				birthDate,
				gender,
				job,
				heightCm,
				bodyType,
				smokingStatus,
				drinkingStatus,
				religion,
				mbti,
				education,
				activityRegion,
				hobbies
			);
		}
	}

	record ApprovalStatusResponse(Long userId, String approvalStatus, String grade) {

		static ApprovalStatusResponse from(UserAccount user) {
			return new ApprovalStatusResponse(
				user.getId(),
				user.getApprovalStatus().name(),
				user.getGrade() == null ? null : user.getGrade().name()
			);
		}
	}

	record ProfileResponse(
		String name,
		LocalDate birthDate,
		String gender,
		String job,
		Integer heightCm,
		String bodyType,
		String smokingStatus,
		String drinkingStatus,
		String religion,
		String mbti,
		String education,
		String activityRegion,
		List<String> hobbies,
		boolean profileCompleted
	) {

		static ProfileResponse from(ProfileView profile) {
			return new ProfileResponse(
				profile.name(),
				profile.birthDate(),
				profile.gender(),
				profile.job(),
				profile.heightCm(),
				profile.bodyType(),
				profile.smokingStatus(),
				profile.drinkingStatus(),
				profile.religion(),
				profile.mbti(),
				profile.education(),
				profile.activityRegion(),
				profile.hobbies(),
				profile.profileCompleted()
			);
		}
	}

	record ProfileSaveResponse(boolean profileCompleted) {
	}

	record IntroPhotoResponse(String intro, String photoUrl, List<IntroPhotoItemResponse> photos, boolean completed) {

		static IntroPhotoResponse from(IntroPhotoView introPhoto) {
			return new IntroPhotoResponse(
				introPhoto.intro(),
				introPhoto.photoUrl(),
				introPhoto.photos().stream()
					.map(photo -> new IntroPhotoItemResponse(photo.photoUrl(), photo.displayOrder()))
					.toList(),
				introPhoto.completed()
			);
		}
	}

	record IntroPhotoItemResponse(String photoUrl, Integer displayOrder) {
	}

	record IntroPhotoSaveResponse(boolean completed) {
	}

	record VerificationDocumentResponse(Long documentId, String reviewStatus) {

		static VerificationDocumentResponse from(UserVerificationDocument document) {
			return new VerificationDocumentResponse(document.getId(), document.getReviewStatus().name());
		}
	}
}
