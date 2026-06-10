package com.oao.backend.dev.service;

import com.oao.backend.config.UploadProperties;
import com.oao.backend.heart.domain.HeartTransaction;
import com.oao.backend.heart.domain.HeartWallet;
import com.oao.backend.heart.repository.HeartTransactionRepository;
import com.oao.backend.heart.repository.HeartWalletRepository;
import com.oao.backend.matching.domain.MatchingProfile;
import com.oao.backend.matching.repository.MatchingProfileRepository;
import com.oao.backend.user.domain.Hobby;
import com.oao.backend.user.domain.OAuthAccount;
import com.oao.backend.user.domain.OAuthAccount.OAuthProvider;
import com.oao.backend.user.domain.ProfilePhoto;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserAccount.ApprovalStatus;
import com.oao.backend.user.domain.UserAccount.Gender;
import com.oao.backend.user.domain.UserAccount.MemberGrade;
import com.oao.backend.user.domain.UserHobby;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.HobbyRepository;
import com.oao.backend.user.repository.OAuthAccountRepository;
import com.oao.backend.user.repository.ProfilePhotoRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserHobbyRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import com.oao.backend.user.service.ProfileIntroPhotoService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevTestUserService {

	private static final String DEV_EMAIL_DOMAIN = "dev.local";
	private static final int DEV_HEART_BALANCE = 30;

	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final OAuthAccountRepository oAuthAccountRepository;
	private final MatchingProfileRepository matchingProfileRepository;
	private final ProfilePhotoRepository profilePhotoRepository;
	private final HobbyRepository hobbyRepository;
	private final UserHobbyRepository userHobbyRepository;
	private final HeartWalletRepository heartWalletRepository;
	private final HeartTransactionRepository heartTransactionRepository;
	private final UploadProperties uploadProperties;

	public DevTestUserService(
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		OAuthAccountRepository oAuthAccountRepository,
		MatchingProfileRepository matchingProfileRepository,
		ProfilePhotoRepository profilePhotoRepository,
		HobbyRepository hobbyRepository,
		UserHobbyRepository userHobbyRepository,
		HeartWalletRepository heartWalletRepository,
		HeartTransactionRepository heartTransactionRepository,
		UploadProperties uploadProperties
	) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.oAuthAccountRepository = oAuthAccountRepository;
		this.matchingProfileRepository = matchingProfileRepository;
		this.profilePhotoRepository = profilePhotoRepository;
		this.hobbyRepository = hobbyRepository;
		this.userHobbyRepository = userHobbyRepository;
		this.heartWalletRepository = heartWalletRepository;
		this.heartTransactionRepository = heartTransactionRepository;
		this.uploadProperties = uploadProperties;
	}

	@Transactional(readOnly = true)
	public List<DevTestUserView> findTestUsers() {
		return oAuthAccountRepository.findAll().stream()
			.filter(account -> account.getEmail() != null && account.getEmail().endsWith("@" + DEV_EMAIL_DOMAIN))
			.map(OAuthAccount::getUserId)
			.distinct()
			.map(this::toViewOrNull)
			.filter(view -> view != null)
			.sorted(Comparator.comparing(DevTestUserView::userId))
			.toList();
	}

	@Transactional
	public DevSeedResult seedTestUsers() {
		List<SeedProfile> seeds = seedProfiles();
		List<DevTestUserView> users = new ArrayList<>();
		int createdCount = 0;
		int reusedCount = 0;

		for (SeedProfile seed : seeds) {
			SeededUser seededUser = createOrUpdate(seed);
			if (seededUser.created()) {
				createdCount++;
			} else {
				reusedCount++;
			}
			users.add(toViewOrNull(seededUser.user().getId()));
		}

		return new DevSeedResult(createdCount, reusedCount, users);
	}

	private SeededUser createOrUpdate(SeedProfile seed) {
		OAuthAccount existingOAuth = oAuthAccountRepository
			.findByProviderAndProviderUserId(OAuthProvider.KAKAO, seed.providerUserId())
			.orElse(null);
		boolean created = existingOAuth == null;
		UserAccount user = created
			? userAccountRepository.save(UserAccount.createPending())
			: userAccountRepository.findById(existingOAuth.getUserId()).orElseGet(() -> userAccountRepository.save(UserAccount.createPending()));

		user.updateBasicInfo(seed.name(), seed.birthDate());
		user.updateGender(seed.gender());
		user.approve(seed.grade(), 0L);

		if (created) {
			oAuthAccountRepository.save(OAuthAccount.connectKakao(user.getId(), seed.providerUserId(), seed.email()));
		}

		UserProfile profile = userProfileRepository.findByUserId(user.getId())
			.orElseGet(() -> userProfileRepository.save(UserProfile.create(user.getId(), null, null, null, null, null, null, null, null, null)));
		profile.updateOnboardingInfo(
			seed.heightCm(),
			seed.bodyType(),
			seed.mbti(),
			seed.job(),
			seed.education(),
			seed.religion(),
			seed.activityRegion(),
			seed.smokingStatus(),
			seed.drinkingStatus()
		);
		profile.verifyPhoneNumber(seed.phoneNumber());

		MatchingProfile matchingProfile = matchingProfileRepository.findByUserId(user.getId())
			.orElseGet(() -> matchingProfileRepository.save(MatchingProfile.create(user.getId())));
		matchingProfile.updateIntro(seed.intro());

		saveHobbies(user.getId(), seed.hobbies());
		saveProfilePhoto(user.getId(), seed);
		chargeWallet(user.getId());

		return new SeededUser(user, created);
	}

	private void saveHobbies(Long userId, List<String> hobbyNames) {
		Map<String, Hobby> hobbiesByName = hobbyRepository.findByNameIn(hobbyNames).stream()
			.collect(Collectors.toMap(Hobby::getName, Function.identity()));
		List<UserHobby> userHobbies = hobbyNames.stream()
			.map(hobbiesByName::get)
			.filter(hobby -> hobby != null)
			.map(hobby -> UserHobby.select(userId, hobby.getId()))
			.toList();

		userHobbyRepository.deleteAll(userHobbyRepository.findByIdUserId(userId));
		userHobbyRepository.saveAll(userHobbies);
	}

	private void saveProfilePhoto(Long userId, SeedProfile seed) {
		String photoUrl = ensureProfilePhotoFile(userId, seed);
		List<ProfilePhoto> existingPhotos = profilePhotoRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId);
		profilePhotoRepository.deleteAll(existingPhotos);
		profilePhotoRepository.flush();
		profilePhotoRepository.saveAll(List.of(
			ProfilePhoto.create(userId, photoUrl, 1),
			ProfilePhoto.create(userId, photoUrl, 2)
		));
	}

	private String ensureProfilePhotoFile(Long userId, SeedProfile seed) {
		String fileName = "dev-user-" + userId + ".svg";
		Path uploadDir = Path.of(uploadProperties.rootDir(), "profile-photos", "dev")
			.toAbsolutePath()
			.normalize();
		Path uploadPath = uploadDir.resolve(fileName);

		try {
			Files.createDirectories(uploadDir);
			Files.writeString(uploadPath, profileSvg(seed), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new IllegalStateException("Dev profile photo could not be written.", exception);
		}

		return trimTrailingSlash(uploadProperties.publicPath()) + "/profile-photos/dev/" + fileName;
	}

	private String profileSvg(SeedProfile seed) {
		String color = seed.gender() == Gender.MALE ? "#1f2937" : "#7c2d12";
		String accent = seed.gender() == Gender.MALE ? "#d6c09b" : "#ffe64a";
		String initial = seed.name().substring(0, 1);
		return """
			<svg xmlns="http://www.w3.org/2000/svg" width="640" height="640" viewBox="0 0 640 640">
			  <rect width="640" height="640" fill="%s"/>
			  <circle cx="320" cy="250" r="118" fill="%s"/>
			  <rect x="150" y="390" width="340" height="150" rx="75" fill="%s" opacity="0.86"/>
			  <text x="320" y="288" text-anchor="middle" font-family="Arial, sans-serif" font-size="104" font-weight="800" fill="#111111">%s</text>
			</svg>
			""".formatted(color, accent, accent, initial);
	}

	private void chargeWallet(Long userId) {
		HeartWallet wallet = heartWalletRepository.findByUserId(userId)
			.orElseGet(() -> heartWalletRepository.save(HeartWallet.create(userId)));
		if (wallet.getBalance() >= DEV_HEART_BALANCE) {
			return;
		}
		int amount = DEV_HEART_BALANCE - wallet.getBalance();
		wallet.charge(amount);
		heartTransactionRepository.save(HeartTransaction.charge(userId, amount, wallet.getBalance(), "DEV_SEED", null));
	}

	private DevTestUserView toViewOrNull(Long userId) {
		UserAccount user = userAccountRepository.findById(userId).orElse(null);
		if (user == null) {
			return null;
		}
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		List<ProfilePhoto> photos = profilePhotoRepository.findByUserIdOrderByDisplayOrderAscIdAsc(userId);
		ProfilePhoto photo = photos.isEmpty() ? null : photos.get(0);
		HeartWallet wallet = heartWalletRepository.findByUserId(userId).orElse(null);
		OAuthAccount oauthAccount = oAuthAccountRepository.findFirstByUserId(userId).orElse(null);

		return new DevTestUserView(
			user.getId(),
			oauthAccount == null ? null : oauthAccount.getEmail(),
			user.getName(),
			user.getGender() == null ? null : user.getGender().name(),
			age(user.getBirthDate()),
			user.getApprovalStatus().name(),
			user.getGrade() == null ? null : user.getGrade().name(),
			profile == null ? null : profile.getJob(),
			profile == null ? null : profile.getActivityRegion(),
			photo == null ? null : photo.getImageUrl(),
			wallet == null ? 0 : wallet.getBalance(),
			isCompleted(user, profile, photos)
		);
	}

	private boolean isCompleted(UserAccount user, UserProfile profile, List<ProfilePhoto> photos) {
		return user.getName() != null
			&& user.getBirthDate() != null
			&& user.getGender() != null
			&& profile != null
			&& profile.getHeightCm() != null
			&& profile.getBodyType() != null
			&& profile.getJob() != null
			&& profile.getActivityRegion() != null
			&& photos.stream()
				.filter(photo -> photo.getImageUrl() != null && !photo.getImageUrl().isBlank())
				.count() >= ProfileIntroPhotoService.MIN_PHOTO_COUNT;
	}

	private Integer age(LocalDate birthDate) {
		return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
	}

	private String trimTrailingSlash(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	private List<SeedProfile> seedProfiles() {
		return List.of(
			new SeedProfile("dev-seed-male-1", "dev.male1@" + DEV_EMAIL_DOMAIN, "김도윤", Gender.MALE, LocalDate.of(1991, 3, 12), MemberGrade.S, 181, "SLIM_FIT", "ENTJ", "핀테크 프로덕트 매니저", "연세대 경영학 학사", "NONE", "서울(강남)", "NON_SMOKER", "SOCIAL", "새로운 경험과 깊은 대화를 좋아해요. 주말에는 전시나 맛집을 자주 갑니다.", "01090000001", List.of("헬스", "전시", "와인", "맛집")),
			new SeedProfile("dev-seed-male-2", "dev.male2@" + DEV_EMAIL_DOMAIN, "박서준", Gender.MALE, LocalDate.of(1989, 8, 4), MemberGrade.A, 176, "AVERAGE", "INTP", "백엔드 개발자", "서울시립대 컴퓨터공학 학사", "NONE", "서울(마포)", "NON_SMOKER", "SOCIAL", "차분하지만 좋아하는 주제에는 오래 이야기하는 편이에요. 같이 성장할 수 있는 관계를 기대해요.", "01090000002", List.of("러닝", "독서", "카페", "게임")),
			new SeedProfile("dev-seed-male-3", "dev.male3@" + DEV_EMAIL_DOMAIN, "이재현", Gender.MALE, LocalDate.of(1994, 11, 22), MemberGrade.B, 184, "MUSCULAR", "ESTP", "브랜드 마케터", "중앙대 광고홍보학 학사", "CHRISTIAN", "서울(성동)", "OCCASIONAL", "OFTEN", "운동과 여행을 좋아하고 에너지가 좋은 사람입니다. 편하게 웃을 수 있는 만남이면 좋겠어요.", "01090000003", List.of("헬스", "여행", "공연", "드라이브")),
			new SeedProfile("dev-seed-female-1", "dev.female1@" + DEV_EMAIL_DOMAIN, "최서윤", Gender.FEMALE, LocalDate.of(1993, 6, 9), MemberGrade.S, 165, "SLIGHT_VOLUME", "ENFJ", "IT 서비스 기획자", "고려대 미디어학 학사", "NONE", "서울(강남)", "NON_SMOKER", "SOCIAL", "따뜻한 대화와 유머를 좋아해요. 같이 산책하고 취향을 나눌 수 있는 분을 만나고 싶어요.", "01090000004", List.of("전시", "요가", "맛집", "와인")),
			new SeedProfile("dev-seed-female-2", "dev.female2@" + DEV_EMAIL_DOMAIN, "정하린", Gender.FEMALE, LocalDate.of(1996, 1, 17), MemberGrade.A, 162, "SLIM_FIT", "INFJ", "약사", "숙명여대 약대 학사", "CATHOLIC", "서울(서초)", "NON_SMOKER", "NONE", "조용한 카페에서 책 읽는 걸 좋아해요. 진중하고 배려 깊은 관계를 만들고 싶습니다.", "01090000005", List.of("독서", "카페", "필라테스", "음악")),
			new SeedProfile("dev-seed-female-3", "dev.female3@" + DEV_EMAIL_DOMAIN, "한지민", Gender.FEMALE, LocalDate.of(1990, 9, 30), MemberGrade.B, 168, "AVERAGE", "ENFP", "영상 프로듀서", "홍익대 시각디자인 학사", "NONE", "서울(마포)", "NON_SMOKER", "OFTEN", "호기심이 많고 새로운 장소를 찾아다니는 걸 좋아해요. 밝고 솔직한 사람과 잘 맞아요.", "01090000006", List.of("영화", "공연", "여행", "사진"))
		);
	}

	private record SeedProfile(
		String providerUserId,
		String email,
		String name,
		Gender gender,
		LocalDate birthDate,
		MemberGrade grade,
		Integer heightCm,
		String bodyType,
		String mbti,
		String job,
		String education,
		String religion,
		String activityRegion,
		String smokingStatus,
		String drinkingStatus,
		String intro,
		String phoneNumber,
		List<String> hobbies
	) {
	}

	private record SeededUser(UserAccount user, boolean created) {
	}

	public record DevSeedResult(int createdCount, int reusedCount, List<DevTestUserView> users) {
	}

	public record DevTestUserView(
		Long userId,
		String email,
		String name,
		String gender,
		Integer age,
		String approvalStatus,
		String grade,
		String job,
		String activityRegion,
		String photoUrl,
		int heartBalance,
		boolean profileCompleted
	) {
	}
}
