package com.oao.backend.matching.service;

import com.oao.backend.matching.domain.MatchingProfile;
import com.oao.backend.matching.repository.MatchingProfileRepository;
import com.oao.backend.user.domain.ProfilePhoto;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserAccount.ApprovalStatus;
import com.oao.backend.user.domain.UserAccount.UserStatus;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.ProfilePhotoRepository;
import com.oao.backend.user.repository.UserHobbyRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MatchingCandidateService {

	private final UserProfileRepository userProfileRepository;
	private final MatchingProfileRepository matchingProfileRepository;
	private final ProfilePhotoRepository profilePhotoRepository;
	private final UserHobbyRepository userHobbyRepository;

	public MatchingCandidateService(
		UserProfileRepository userProfileRepository,
		MatchingProfileRepository matchingProfileRepository,
		ProfilePhotoRepository profilePhotoRepository,
		UserHobbyRepository userHobbyRepository
	) {
		this.userProfileRepository = userProfileRepository;
		this.matchingProfileRepository = matchingProfileRepository;
		this.profilePhotoRepository = profilePhotoRepository;
		this.userHobbyRepository = userHobbyRepository;
	}

	public Optional<MatchingCandidate> toCandidate(UserAccount user) {
		if (user.getStatus() != UserStatus.ACTIVE
			|| user.getApprovalStatus() != ApprovalStatus.APPROVED
			|| user.getGender() == null
			|| user.getName() == null
			|| user.getName().isBlank()
			|| user.getBirthDate() == null) {
			return Optional.empty();
		}

		UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
		if (!isProfileComplete(profile)) {
			return Optional.empty();
		}

		MatchingProfile matchingProfile = matchingProfileRepository.findByUserId(user.getId()).orElse(null);
		if (matchingProfile == null || matchingProfile.getJobIntro() == null || matchingProfile.getJobIntro().isBlank()) {
			return Optional.empty();
		}

		ProfilePhoto photo = profilePhotoRepository.findFirstByUserIdOrderByDisplayOrderAscIdAsc(user.getId()).orElse(null);
		if (photo == null || photo.getImageUrl() == null || photo.getImageUrl().isBlank()) {
			return Optional.empty();
		}

		Set<Long> hobbyIds = userHobbyRepository.findByIdUserId(user.getId()).stream()
			.map(userHobby -> userHobby.getId().getHobbyId())
			.collect(Collectors.toSet());
		if (hobbyIds.isEmpty()) {
			return Optional.empty();
		}

		return Optional.of(new MatchingCandidate(
			user.getId(),
			user.getGender(),
			user.getGrade(),
			profile.getSmokingStatus(),
			profile.getDrinkingStatus(),
			profile.getReligion(),
			hobbyIds,
			matchingProfile.getLastAutoMatchedAt(),
			matchingProfile.getAutoMatchCount()
		));
	}

	private boolean isProfileComplete(UserProfile profile) {
		return profile != null
			&& profile.getHeightCm() != null
			&& profile.getJob() != null
			&& !profile.getJob().isBlank()
			&& profile.getEducation() != null
			&& !profile.getEducation().isBlank()
			&& profile.getActivityRegion() != null
			&& !profile.getActivityRegion().isBlank()
			&& profile.getBodyType() != null
			&& !profile.getBodyType().isBlank()
			&& profile.getSmokingStatus() != null
			&& profile.getDrinkingStatus() != null
			&& profile.getReligion() != null
			&& profile.getMbti() != null;
	}
}
