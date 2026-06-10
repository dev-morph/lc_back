package com.oao.backend.user.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.Hobby;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserAccount.Gender;
import com.oao.backend.user.domain.UserHobby;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.HobbyRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserHobbyRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

	private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);
	private static final Set<String> SMOKING_STATUSES = Set.of("NON_SMOKER", "SMOKER", "OCCASIONAL");
	private static final Set<String> DRINKING_STATUSES = Set.of("NONE", "SOCIAL", "OFTEN");
	private static final Set<String> RELIGIONS = Set.of("NONE", "CHRISTIAN", "CATHOLIC", "BUDDHIST", "OTHER");
	private static final Set<String> MALE_BODY_TYPES = Set.of("THIN", "SLIM_FIT", "AVERAGE", "CHUBBY", "STURDY", "MUSCULAR");
	private static final Set<String> FEMALE_BODY_TYPES = Set.of("THIN", "SLIM_FIT", "AVERAGE", "SLIGHT_VOLUME", "GLAMOROUS");
	private static final int MIN_HOBBY_COUNT = 1;
	private static final int MAX_HOBBY_COUNT = 8;
	private static final Set<String> MBTIS = Set.of(
		"ISTJ",
		"ISFJ",
		"INFJ",
		"INTJ",
		"ISTP",
		"ISFP",
		"INFP",
		"INTP",
		"ESTP",
		"ESFP",
		"ENFP",
		"ENTP",
		"ESTJ",
		"ESFJ",
		"ENFJ",
		"ENTJ",
		"UNKNOWN"
	);
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final HobbyRepository hobbyRepository;
	private final UserHobbyRepository userHobbyRepository;

	public UserProfileService(
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		HobbyRepository hobbyRepository,
		UserHobbyRepository userHobbyRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.hobbyRepository = hobbyRepository;
		this.userHobbyRepository = userHobbyRepository;
	}

	@Transactional(readOnly = true)
	public ProfileView findProfile(Long userId) {
		UserAccount user = findUser(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		List<String> hobbies = findHobbyNames(userId);
		return ProfileView.from(user, profile, hobbies, isProfileCompleted(user, profile, hobbies));
	}

	@Transactional
	public ProfileView updateProfile(Long userId, ProfileUpdateCommand command) {
		validate(command);

		UserAccount user = findUser(userId);
		String name = trim(command.name());
		String job = trim(command.job());
		String education = trim(command.education());
		String activityRegion = trim(command.activityRegion());
		List<Hobby> selectedHobbies = selectedHobbies(command.hobbies());

		user.updateBasicInfo(name, command.birthDate());
		user.updateGender(command.gender());
		UserProfile profile = userProfileRepository.findByUserId(userId)
			.map(existingProfile -> {
				existingProfile.updateOnboardingInfo(
					command.heightCm(),
					command.bodyType(),
					command.mbti(),
					job,
					education,
					command.religion(),
					activityRegion,
					command.smokingStatus(),
					command.drinkingStatus()
				);
				return existingProfile;
			})
			.orElseGet(() -> userProfileRepository.save(UserProfile.create(
				userId,
				command.heightCm(),
				command.bodyType(),
				command.mbti(),
				job,
				education,
				command.religion(),
				activityRegion,
				command.smokingStatus(),
				command.drinkingStatus()
			)));
		replaceHobbies(userId, selectedHobbies);

		List<String> hobbies = selectedHobbies.stream().map(Hobby::getName).toList();
		return ProfileView.from(user, profile, hobbies, isProfileCompleted(user, profile, hobbies));
	}

	private UserAccount findUser(Long userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found."));
	}

	private void validate(ProfileUpdateCommand command) {
		if (command == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Profile data is required.");
		}
		if (trim(command.name()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Name must be at least 2 characters.");
		}
		if (command.birthDate() == null
			|| command.birthDate().isBefore(MIN_BIRTH_DATE)
			|| command.birthDate().isAfter(LocalDate.now())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Birth date is invalid.");
		}
		if (command.gender() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Gender is required.");
		}
		if (trim(command.job()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Job must be at least 2 characters.");
		}
		if (command.heightCm() == null || command.heightCm() < 120 || command.heightCm() > 230) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Height must be between 120cm and 230cm.");
		}
		requireBodyTypeAllowed(command.gender(), command.bodyType());

		requireAllowed("smokingStatus", command.smokingStatus(), SMOKING_STATUSES);
		requireAllowed("drinkingStatus", command.drinkingStatus(), DRINKING_STATUSES);
		requireAllowed("religion", command.religion(), RELIGIONS);
		requireAllowed("mbti", command.mbti(), MBTIS);
		if (trim(command.education()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Education must be at least 2 characters.");
		}
		if (trim(command.activityRegion()).length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Activity region must be at least 2 characters.");
		}
		validateHobbyNames(command.hobbies());
	}

	private boolean isProfileCompleted(UserAccount user, UserProfile profile, List<String> hobbies) {
		return trim(user.getName()).length() >= 2
			&& user.getBirthDate() != null
			&& user.getGender() != null
			&& !user.getBirthDate().isBefore(MIN_BIRTH_DATE)
			&& !user.getBirthDate().isAfter(LocalDate.now())
			&& profile != null
			&& profile.getHeightCm() != null
			&& profile.getHeightCm() >= 120
			&& profile.getHeightCm() <= 230
			&& isBodyTypeAllowed(user.getGender(), profile.getBodyType())
			&& trim(profile.getJob()).length() >= 2
			&& SMOKING_STATUSES.contains(profile.getSmokingStatus())
			&& DRINKING_STATUSES.contains(profile.getDrinkingStatus())
			&& RELIGIONS.contains(profile.getReligion())
			&& MBTIS.contains(profile.getMbti())
			&& trim(profile.getEducation()).length() >= 2
			&& trim(profile.getActivityRegion()).length() >= 2
			&& hobbies.size() >= MIN_HOBBY_COUNT
			&& hobbies.size() <= MAX_HOBBY_COUNT;
	}

	private void validateHobbyNames(List<String> hobbies) {
		if (hobbies == null || hobbies.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "At least one hobby is required.");
		}
		if (normalizedHobbyNames(hobbies).size() > MAX_HOBBY_COUNT) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Hobbies can be up to " + MAX_HOBBY_COUNT + ".");
		}
	}

	private List<Hobby> selectedHobbies(List<String> hobbyNames) {
		List<String> normalizedNames = normalizedHobbyNames(hobbyNames);
		List<Hobby> hobbies = hobbyRepository.findByNameIn(normalizedNames);
		Map<String, Hobby> hobbyByName = hobbies.stream()
			.collect(Collectors.toMap(Hobby::getName, Function.identity()));

		List<String> missingNames = normalizedNames.stream()
			.filter(name -> !hobbyByName.containsKey(name))
			.toList();
		if (!missingNames.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Hobby is invalid.");
		}

		return normalizedNames.stream().map(hobbyByName::get).toList();
	}

	private List<String> normalizedHobbyNames(List<String> hobbies) {
		if (hobbies == null) {
			return List.of();
		}
		return hobbies.stream()
			.map(this::trim)
			.filter(value -> !value.isBlank())
			.collect(Collectors.collectingAndThen(
				Collectors.toCollection(LinkedHashSet::new),
				List::copyOf
			));
	}

	private List<String> findHobbyNames(Long userId) {
		List<Long> hobbyIds = userHobbyRepository.findByIdUserId(userId).stream()
			.map(userHobby -> userHobby.getId().getHobbyId())
			.toList();
		if (hobbyIds.isEmpty()) {
			return List.of();
		}
		Map<Long, Hobby> hobbyById = hobbyRepository.findAllById(hobbyIds).stream()
			.collect(Collectors.toMap(Hobby::getId, Function.identity()));
		return hobbyIds.stream()
			.map(hobbyById::get)
			.filter(hobby -> hobby != null)
			.map(Hobby::getName)
			.toList();
	}

	private void replaceHobbies(Long userId, List<Hobby> hobbies) {
		userHobbyRepository.deleteByIdUserId(userId);
		userHobbyRepository.saveAll(hobbies.stream()
			.map(hobby -> UserHobby.select(userId, hobby.getId()))
			.toList());
	}

	private void requireAllowed(String fieldName, String value, Set<String> allowedValues) {
		if (!allowedValues.contains(value)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, fieldName + " is invalid.");
		}
	}

	private void requireBodyTypeAllowed(Gender gender, String bodyType) {
		if (!isBodyTypeAllowed(gender, bodyType)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "bodyType is invalid.");
		}
	}

	private boolean isBodyTypeAllowed(Gender gender, String bodyType) {
		if (gender == Gender.MALE) {
			return MALE_BODY_TYPES.contains(bodyType);
		}
		if (gender == Gender.FEMALE) {
			return FEMALE_BODY_TYPES.contains(bodyType);
		}
		return false;
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}

	public record ProfileUpdateCommand(
		String name,
		LocalDate birthDate,
		Gender gender,
		String job,
		Integer heightCm,
		String bodyType,
		String smokingStatus,
		String drinkingStatus,
		String religion,
		String mbti,
		String education,
		String activityRegion,
		List<String> hobbies
	) {
	}

	public record ProfileView(
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

		static ProfileView from(UserAccount user, UserProfile profile, List<String> hobbies, boolean profileCompleted) {
			return new ProfileView(
				user.getName(),
				user.getBirthDate(),
				user.getGender() == null ? null : user.getGender().name(),
				profile == null ? null : profile.getJob(),
				profile == null ? null : profile.getHeightCm(),
				profile == null ? null : profile.getBodyType(),
				profile == null ? null : profile.getSmokingStatus(),
				profile == null ? null : profile.getDrinkingStatus(),
				profile == null ? null : profile.getReligion(),
				profile == null ? null : profile.getMbti(),
				profile == null ? null : profile.getEducation(),
				profile == null ? null : profile.getActivityRegion(),
				hobbies,
				profileCompleted
			);
		}
	}
}
