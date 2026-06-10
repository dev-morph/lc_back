package com.oao.backend.user.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.matching.domain.MatchingProfile;
import com.oao.backend.matching.repository.MatchingProfileRepository;
import com.oao.backend.user.domain.Hobby;
import com.oao.backend.user.domain.OAuthAccount;
import com.oao.backend.user.domain.ProfilePhoto;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserAccount.ApprovalStatus;
import com.oao.backend.user.domain.UserAccount.Gender;
import com.oao.backend.user.domain.UserAccount.MemberGrade;
import com.oao.backend.user.domain.UserGradeHistory;
import com.oao.backend.user.domain.UserHobby;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.HobbyRepository;
import com.oao.backend.user.repository.OAuthAccountRepository;
import com.oao.backend.user.repository.ProfilePhotoRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserGradeHistoryRepository;
import com.oao.backend.user.repository.UserHobbyRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserApprovalService {

	private final UserAccountRepository userAccountRepository;
	private final UserGradeHistoryRepository userGradeHistoryRepository;
	private final UserProfileRepository userProfileRepository;
	private final MatchingProfileRepository matchingProfileRepository;
	private final ProfilePhotoRepository profilePhotoRepository;
	private final OAuthAccountRepository oAuthAccountRepository;
	private final ProfileIntroPhotoService profileIntroPhotoService;
	private final HobbyRepository hobbyRepository;
	private final UserHobbyRepository userHobbyRepository;

	public UserApprovalService(
		UserAccountRepository userAccountRepository,
		UserGradeHistoryRepository userGradeHistoryRepository,
		UserProfileRepository userProfileRepository,
		MatchingProfileRepository matchingProfileRepository,
		ProfilePhotoRepository profilePhotoRepository,
		OAuthAccountRepository oAuthAccountRepository,
		ProfileIntroPhotoService profileIntroPhotoService,
		HobbyRepository hobbyRepository,
		UserHobbyRepository userHobbyRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.userGradeHistoryRepository = userGradeHistoryRepository;
		this.userProfileRepository = userProfileRepository;
		this.matchingProfileRepository = matchingProfileRepository;
		this.profilePhotoRepository = profilePhotoRepository;
		this.oAuthAccountRepository = oAuthAccountRepository;
		this.profileIntroPhotoService = profileIntroPhotoService;
		this.hobbyRepository = hobbyRepository;
		this.userHobbyRepository = userHobbyRepository;
	}

	@Transactional(readOnly = true)
	public List<UserAccount> findPendingUsers() {
		return userAccountRepository.findByApprovalStatus(ApprovalStatus.PENDING);
	}

	@Transactional(readOnly = true)
	public List<PendingApprovalUserView> findPendingUserViews() {
		return findPendingUsers().stream()
			.map(this::toPendingApprovalUserView)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<AdminUserSearchView> searchUsers(AdminUserSearchCommand command) {
		AdminUserSearchCommand normalizedCommand = command == null ? AdminUserSearchCommand.empty() : command;
		return userAccountRepository.findAll().stream()
			.filter(user -> matchesSearch(user, normalizedCommand))
			.map(this::toAdminUserSearchView)
			.toList();
	}

	@Transactional
	public AdminUserSearchView createAdminUser(
		AdminUserCreateCommand command,
		List<MultipartFile> photos,
		MultipartFile legacyPhoto,
		Long adminId
	) {
		validateCreateCommand(command, photos, legacyPhoto);

		UserAccount user = userAccountRepository.save(UserAccount.createPending());
		user.updateBasicInfo(command.name().trim(), command.birthDate());
		user.updateGender(command.gender());
		UserProfile profile = userProfileRepository.save(UserProfile.create(
			user.getId(),
			command.heightCm(),
			command.mbti(),
			command.job().trim(),
			command.education().trim(),
			command.religion(),
			command.activityRegion().trim(),
			command.smokingStatus(),
			command.drinkingStatus()
		));
		List<Hobby> selectedHobbies = selectedHobbies(command.hobbies());
		userHobbyRepository.saveAll(selectedHobbies.stream()
			.map(hobby -> UserHobby.select(user.getId(), hobby.getId()))
			.toList());

		MemberGrade previousGrade = user.getGrade();
		user.approve(command.grade(), adminId);
		userGradeHistoryRepository.save(UserGradeHistory.record(
			user.getId(),
			previousGrade,
			command.grade(),
			adminId,
			"관리자 유저 생성"
		));
		profileIntroPhotoService.saveIntroPhoto(user.getId(), command.intro(), photos, legacyPhoto);

		return toAdminUserSearchView(user, profile, null);
	}

	@Transactional(readOnly = true)
	public AdminUserDetailView findAdminUserDetail(Long userId) {
		UserAccount user = findUser(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		MatchingProfile matchingProfile = matchingProfileRepository.findByUserId(userId).orElse(null);
		ProfilePhoto photo = profilePhotoRepository.findFirstByUserIdOrderByDisplayOrderAscIdAsc(userId).orElse(null);
		OAuthAccount oauthAccount = oAuthAccountRepository.findFirstByUserId(userId).orElse(null);

		return new AdminUserDetailView(
			user.getId(),
			oauthAccount == null ? null : oauthAccount.getEmail(),
			user.getName(),
			user.getBirthDate(),
			user.getGender() == null ? null : user.getGender().name(),
			user.getStatus().name(),
			user.getApprovalStatus().name(),
			user.getGrade() == null ? null : user.getGrade().name(),
			profile == null ? null : profile.getJob(),
			profile == null ? null : profile.getHeightCm(),
			profile == null ? null : profile.getActivityRegion(),
			profile == null ? null : profile.getMbti(),
			profile == null ? null : profile.getEducation(),
			profile == null ? null : profile.getSmokingStatus(),
			profile == null ? null : profile.getDrinkingStatus(),
			profile == null ? null : profile.getReligion(),
			matchingProfile == null ? null : matchingProfile.getJobIntro(),
			photo == null ? null : photo.getImageUrl()
		);
	}

	@Transactional
	public AdminUserDetailView updateAdminUser(Long userId, AdminUserUpdateCommand command) {
		validateUpdateCommand(command);

		UserAccount user = findUser(userId);
		user.updateBasicInfo(command.name().trim(), command.birthDate());
		user.updateGender(command.gender());
		if (command.grade() != null && command.grade() != user.getGrade()) {
			user.changeGrade(command.grade());
		}

		UserProfile profile = userProfileRepository.findByUserId(userId)
			.map(existingProfile -> {
				existingProfile.updateOnboardingInfo(
					command.heightCm(),
					command.mbti(),
					command.job().trim(),
					command.education().trim(),
					command.religion(),
					command.activityRegion().trim(),
					command.smokingStatus(),
					command.drinkingStatus()
				);
				return existingProfile;
			})
			.orElseGet(() -> userProfileRepository.save(UserProfile.create(
				userId,
				command.heightCm(),
				command.mbti(),
				command.job().trim(),
				command.education().trim(),
				command.religion(),
				command.activityRegion().trim(),
				command.smokingStatus(),
				command.drinkingStatus()
			)));

		profileIntroPhotoService.saveIntroPhoto(userId, command.intro(), null);

		return findAdminUserDetail(user.getId());
	}

	@Transactional
	public AdminUserSearchView suspendAdminUser(Long userId) {
		UserAccount user = findUser(userId);
		user.suspend();
		return toAdminUserSearchView(user);
	}

	@Transactional
	public AdminUserSearchView deleteAdminUser(Long userId) {
		UserAccount user = findUser(userId);
		user.delete();
		return toAdminUserSearchView(user);
	}

	@Transactional
	public UserAccount approve(Long userId, MemberGrade grade, Long adminId, String reason) {
		UserAccount user = findUser(userId);
		MemberGrade previousGrade = user.getGrade();
		user.approve(grade, adminId);
		userGradeHistoryRepository.save(UserGradeHistory.record(userId, previousGrade, grade, adminId, reason));
		return user;
	}

	@Transactional
	public UserAccount reject(Long userId) {
		UserAccount user = findUser(userId);
		user.reject();
		return user;
	}

	@Transactional
	public UserAccount changeGrade(Long userId, MemberGrade grade, Long adminId, String reason) {
		UserAccount user = findUser(userId);
		MemberGrade previousGrade = user.getGrade();
		user.changeGrade(grade);
		userGradeHistoryRepository.save(UserGradeHistory.record(userId, previousGrade, grade, adminId, reason));
		return user;
	}

	private UserAccount findUser(Long userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found."));
	}

	private PendingApprovalUserView toPendingApprovalUserView(UserAccount user) {
		UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
		MatchingProfile matchingProfile = matchingProfileRepository.findByUserId(user.getId()).orElse(null);
		ProfilePhoto photo = profilePhotoRepository.findFirstByUserIdOrderByDisplayOrderAscIdAsc(user.getId()).orElse(null);
		OAuthAccount oauthAccount = oAuthAccountRepository.findFirstByUserId(user.getId()).orElse(null);

		return new PendingApprovalUserView(
			user.getId(),
			oauthAccount == null ? null : oauthAccount.getEmail(),
			user.getName(),
			user.getBirthDate(),
			profile == null ? null : profile.getJob(),
			profile == null ? null : profile.getHeightCm(),
			profile == null ? null : profile.getActivityRegion(),
			profile == null ? null : profile.getMbti(),
			profile == null ? null : profile.getEducation(),
			profile == null ? null : profile.getSmokingStatus(),
			profile == null ? null : profile.getDrinkingStatus(),
			profile == null ? null : profile.getReligion(),
			matchingProfile == null ? null : matchingProfile.getJobIntro(),
			photo == null ? null : photo.getImageUrl(),
			user.getApprovalStatus(),
			user.getGrade()
		);
	}

	private AdminUserSearchView toAdminUserSearchView(UserAccount user) {
		UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
		OAuthAccount oauthAccount = oAuthAccountRepository.findFirstByUserId(user.getId()).orElse(null);

		return toAdminUserSearchView(user, profile, oauthAccount);
	}

	private AdminUserSearchView toAdminUserSearchView(UserAccount user, UserProfile profile, OAuthAccount oauthAccount) {
		return new AdminUserSearchView(
			user.getId(),
			oauthAccount == null ? null : oauthAccount.getEmail(),
			user.getName(),
			user.getBirthDate(),
			user.getGender() == null ? null : user.getGender().name(),
			user.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
			user.getStatus().name(),
			user.getApprovalStatus().name(),
			user.getGrade() == null ? null : user.getGrade().name(),
			profile == null ? null : profile.getJob(),
			profile == null ? null : profile.getActivityRegion()
		);
	}

	private boolean matchesSearch(UserAccount user, AdminUserSearchCommand command) {
		if (command.gender() != null && !Objects.equals(enumName(user.getGender()), command.gender())) {
			return false;
		}
		if (command.grade() != null && !Objects.equals(enumName(user.getGrade()), command.grade())) {
			return false;
		}
		if (command.status() != null && !Objects.equals(enumName(user.getStatus()), command.status())) {
			return false;
		}
		if (command.approvalStatus() != null && !Objects.equals(enumName(user.getApprovalStatus()), command.approvalStatus())) {
			return false;
		}
		if (command.name() != null && !normalize(user.getName()).contains(normalize(command.name()))) {
			return false;
		}
		if (!matchesAge(user.getBirthDate(), command.minAge(), command.maxAge())) {
			return false;
		}
		LocalDate joinedAt = user.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
		if (command.joinedFrom() != null && joinedAt.isBefore(command.joinedFrom())) {
			return false;
		}
		return command.joinedTo() == null || !joinedAt.isAfter(command.joinedTo());
	}

	private boolean matchesAge(LocalDate birthDate, Integer minAge, Integer maxAge) {
		if (minAge == null && maxAge == null) {
			return true;
		}
		if (birthDate == null) {
			return false;
		}
		int age = java.time.Period.between(birthDate, LocalDate.now()).getYears();
		if (minAge != null && age < minAge) {
			return false;
		}
		return maxAge == null || age <= maxAge;
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private String enumName(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private void validateCreateCommand(AdminUserCreateCommand command, List<MultipartFile> photos, MultipartFile legacyPhoto) {
		if (command == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "User data is required.");
		}
		if (normalize(command.name()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Name must be at least 2 characters.");
		}
		if (command.birthDate() == null || command.birthDate().isAfter(LocalDate.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Birth date is invalid.");
		}
		if (command.gender() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Gender is required.");
		}
		if (command.grade() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Grade is required.");
		}
		if (normalize(command.job()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Job must be at least 2 characters.");
		}
		if (command.heightCm() == null || command.heightCm() < 120 || command.heightCm() > 230) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Height must be between 120cm and 230cm.");
		}
		if (normalize(command.activityRegion()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Activity region must be at least 2 characters.");
		}
		if (normalize(command.education()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Education must be at least 2 characters.");
		}
		if (normalize(command.intro()).length() < 10) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Intro must be at least 10 characters.");
		}
		if (uploadedPhotoCount(photos, legacyPhoto) < ProfileIntroPhotoService.MIN_PHOTO_COUNT) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Profile photos must be at least " + ProfileIntroPhotoService.MIN_PHOTO_COUNT + ".");
		}
		if (command.hobbies() == null || command.hobbies().isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "At least one hobby is required.");
		}
		if (command.hobbies().size() > 8) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Hobbies can be up to 8.");
		}
	}

	private int uploadedPhotoCount(List<MultipartFile> photos, MultipartFile legacyPhoto) {
		int photoCount = photos == null ? 0 : (int) photos.stream()
			.filter(photo -> photo != null && !photo.isEmpty())
			.count();
		if (photoCount == 0 && legacyPhoto != null && !legacyPhoto.isEmpty()) {
			return 1;
		}
		return photoCount;
	}

	private List<Hobby> selectedHobbies(List<String> hobbyNames) {
		List<String> normalizedNames = hobbyNames == null ? List.of() : hobbyNames.stream()
			.map(value -> value == null ? "" : value.trim())
			.filter(value -> !value.isBlank())
			.distinct()
			.toList();
		List<Hobby> hobbies = hobbyRepository.findByNameIn(normalizedNames);
		Map<String, Hobby> hobbyByName = hobbies.stream()
			.collect(Collectors.toMap(Hobby::getName, Function.identity()));
		if (normalizedNames.stream().anyMatch(name -> !hobbyByName.containsKey(name))) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Hobby is invalid.");
		}
		return normalizedNames.stream().map(hobbyByName::get).toList();
	}

	private void validateUpdateCommand(AdminUserUpdateCommand command) {
		if (command == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "User data is required.");
		}
		if (normalize(command.name()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Name must be at least 2 characters.");
		}
		if (command.birthDate() == null || command.birthDate().isAfter(LocalDate.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Birth date is invalid.");
		}
		if (command.gender() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Gender is required.");
		}
		if (command.grade() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Grade is required.");
		}
		if (normalize(command.job()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Job must be at least 2 characters.");
		}
		if (command.heightCm() == null || command.heightCm() < 120 || command.heightCm() > 230) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Height must be between 120cm and 230cm.");
		}
		if (normalize(command.activityRegion()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Activity region must be at least 2 characters.");
		}
		if (normalize(command.education()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Education must be at least 2 characters.");
		}
		if (normalize(command.intro()).length() < 10) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Intro must be at least 10 characters.");
		}
	}

	public record PendingApprovalUserView(
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
		ApprovalStatus approvalStatus,
		MemberGrade grade
	) {
	}

	public record AdminUserSearchCommand(
		String gender,
		Integer minAge,
		Integer maxAge,
		LocalDate joinedFrom,
		LocalDate joinedTo,
		String grade,
		String name,
		String status,
		String approvalStatus
	) {

		static AdminUserSearchCommand empty() {
			return new AdminUserSearchCommand(null, null, null, null, null, null, null, null, null);
		}
	}

	public record AdminUserSearchView(
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
	}

	public record AdminUserDetailView(
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
	}

	public record AdminUserCreateCommand(
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
		String intro,
		List<String> hobbies
	) {
	}

	public record AdminUserUpdateCommand(
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
	}
}
