package com.oao.backend.matching.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.domain.MatchProposal.MatchStatus;
import com.oao.backend.matching.domain.MatchingProfile;
import com.oao.backend.matching.domain.MatchingScoreConfig;
import com.oao.backend.matching.repository.MatchProposalRepository;
import com.oao.backend.matching.repository.MatchingProfileRepository;
import com.oao.backend.notification.service.AppNotificationService;
import com.oao.backend.user.domain.Hobby;
import com.oao.backend.user.domain.ProfilePhoto;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserAccount.ApprovalStatus;
import com.oao.backend.user.domain.UserAccount.UserStatus;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.HobbyRepository;
import com.oao.backend.user.repository.ProfilePhotoRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserHobbyRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMatchingOperationsService {

	private static final Set<MatchStatus> ACTIVE_MATCH_STATUSES = Set.of(MatchStatus.PENDING, MatchStatus.ACCEPTED);
	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

	private final int proposalValidDays;
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final MatchingProfileRepository matchingProfileRepository;
	private final ProfilePhotoRepository profilePhotoRepository;
	private final UserHobbyRepository userHobbyRepository;
	private final HobbyRepository hobbyRepository;
	private final MatchProposalRepository matchProposalRepository;
	private final MatchingCandidateService matchingCandidateService;
	private final MatchingScoreConfigService matchingScoreConfigService;
	private final MatchingScoreCalculator matchingScoreCalculator;
	private final AppNotificationService notificationService;

	public AdminMatchingOperationsService(
		@Value("${oao.matching.proposal-valid-days:3}") int proposalValidDays,
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		MatchingProfileRepository matchingProfileRepository,
		ProfilePhotoRepository profilePhotoRepository,
		UserHobbyRepository userHobbyRepository,
		HobbyRepository hobbyRepository,
		MatchProposalRepository matchProposalRepository,
		MatchingCandidateService matchingCandidateService,
		MatchingScoreConfigService matchingScoreConfigService,
		MatchingScoreCalculator matchingScoreCalculator,
		AppNotificationService notificationService
	) {
		this.proposalValidDays = proposalValidDays;
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.matchingProfileRepository = matchingProfileRepository;
		this.profilePhotoRepository = profilePhotoRepository;
		this.userHobbyRepository = userHobbyRepository;
		this.hobbyRepository = hobbyRepository;
		this.matchProposalRepository = matchProposalRepository;
		this.matchingCandidateService = matchingCandidateService;
		this.matchingScoreConfigService = matchingScoreConfigService;
		this.matchingScoreCalculator = matchingScoreCalculator;
		this.notificationService = notificationService;
	}

	@Transactional(readOnly = true)
	public MatchingOperationsView findOperations(LocalDate date) {
		LocalDate selectedDate = date == null ? LocalDate.now(DEFAULT_ZONE) : date;
		Instant from = selectedDate.atStartOfDay(DEFAULT_ZONE).toInstant();
		Instant to = selectedDate.plusDays(1).atStartOfDay(DEFAULT_ZONE).toInstant();

		List<MatchProposal> matches = matchProposalRepository
			.findByMatchedAtGreaterThanEqualAndMatchedAtLessThanOrderByMatchedAtDescIdDesc(from, to);
		List<EligibleUserView> eligibleUsers = eligibleUsers();
		Set<Long> matchedUserIds = matches.stream()
			.flatMap(match -> List.of(match.getUserAId(), match.getUserBId()).stream())
			.collect(Collectors.toSet());
		List<EligibleUserView> unmatchedUsers = eligibleUsers.stream()
			.filter(user -> !matchedUserIds.contains(user.userId()))
			.toList();

		return new MatchingOperationsView(
			selectedDate,
			new MatchingOperationsSummary(
				matches.size(),
				countByStatus(matches, MatchStatus.PENDING),
				countByStatus(matches, MatchStatus.ACCEPTED),
				countByStatus(matches, MatchStatus.REJECTED),
				unmatchedUsers.size()
			),
			matches.stream().map(this::toMatchOperationView).toList(),
			unmatchedUsers
		);
	}

	@Transactional
	public ManualMatchResult createManualMatch(ManualMatchCommand command, Long adminId) {
		if (command.userAId().equals(command.userBId())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Users must be different.");
		}

		EligibleUserView userA = eligibleUser(command.userAId());
		EligibleUserView userB = eligibleUser(command.userBId());
		if (userA.gender() == null || userA.gender().equals(userB.gender())) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Manual match requires different genders.");
		}
		if (matchProposalRepository.existsActivePair(userA.userId(), userB.userId(), ACTIVE_MATCH_STATUSES)) {
			throw new BusinessException(HttpStatus.CONFLICT, "Active match already exists.");
		}

		Instant now = Instant.now();
		String reason = command.reason() == null || command.reason().isBlank()
			? "관리자가 프로필 정보를 바탕으로 추천했어요."
			: command.reason().trim();
		MatchProposal proposal = matchProposalRepository.save(MatchProposal.createManual(
			userA.userId(),
			userB.userId(),
			adminId,
			reason,
			now,
			now.plus(Math.max(1, proposalValidDays), ChronoUnit.DAYS)
		));
		notificationService.notifyMatchArrived(proposal.getId(), userA.userId(), userB.userId());

		return new ManualMatchResult(proposal.getId(), userA.userId(), userB.userId(), reason);
	}

	@Transactional(readOnly = true)
	public ManualMatchScorePreview previewManualMatchScore(ManualMatchScoreCommand command) {
		if (command.userAId().equals(command.userBId())) {
			return ManualMatchScorePreview.ineligible("서로 다른 유저를 선택해주세요.");
		}

		UserAccount userA = userAccountRepository.findById(command.userAId()).orElse(null);
		UserAccount userB = userAccountRepository.findById(command.userBId()).orElse(null);
		if (userA == null || userB == null) {
			return ManualMatchScorePreview.ineligible("선택한 유저를 찾을 수 없습니다.");
		}

		MatchingCandidate candidateA = matchingCandidateService.toCandidate(userA).orElse(null);
		MatchingCandidate candidateB = matchingCandidateService.toCandidate(userB).orElse(null);
		if (candidateA == null || candidateB == null) {
			return ManualMatchScorePreview.ineligible("매칭 가능한 프로필 상태가 아닙니다.");
		}
		if (candidateA.gender() == null || candidateA.gender().equals(candidateB.gender())) {
			return ManualMatchScorePreview.ineligible("수동 매칭은 서로 다른 성별만 가능합니다.");
		}
		if (matchProposalRepository.existsActivePair(candidateA.userId(), candidateB.userId(), ACTIVE_MATCH_STATUSES)) {
			return ManualMatchScorePreview.ineligible("이미 진행 중인 매칭이 있는 조합입니다.");
		}

		MatchingScoreConfig config = matchingScoreConfigService.findOrDefault();
		MatchingScore score = matchingScoreCalculator.calculate(candidateA, candidateB, config);
		return ManualMatchScorePreview.eligible(
			score.totalScore(),
			score.sharedHobbyCount(),
			score.hobbyScore(),
			score.smokingScore(),
			score.drinkingScore(),
			score.religionScore(),
			score.gradeScore(),
			"수동 매칭 가능한 조합입니다."
		);
	}

	private MatchOperationView toMatchOperationView(MatchProposal match) {
		return new MatchOperationView(
			match.getId(),
			match.getMatchType().name(),
			match.getStatus().name(),
			match.getMatchedAt(),
			match.getExpiresAt(),
			match.getUserADecision().name(),
			match.getUserBDecision().name(),
			match.getMatchedReason(),
			userSummary(match.getUserAId()),
			userSummary(match.getUserBId())
		);
	}

	private EligibleUserView eligibleUser(Long userId) {
		return eligibleUsers().stream()
			.filter(user -> user.userId().equals(userId))
			.findFirst()
			.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "User is not eligible for matching."));
	}

	private List<EligibleUserView> eligibleUsers() {
		return userAccountRepository.findAll().stream()
			.map(this::toEligibleUserOrNull)
			.filter(user -> user != null)
			.sorted(Comparator.comparing(EligibleUserView::userId))
			.toList();
	}

	private EligibleUserView toEligibleUserOrNull(UserAccount user) {
		if (user.getStatus() != UserStatus.ACTIVE
			|| user.getApprovalStatus() != ApprovalStatus.APPROVED
			|| user.getGender() == null
			|| user.getName() == null
			|| user.getName().isBlank()
			|| user.getBirthDate() == null) {
			return null;
		}

		UserProfile profile = userProfileRepository.findByUserId(user.getId()).orElse(null);
		MatchingProfile matchingProfile = matchingProfileRepository.findByUserId(user.getId()).orElse(null);
		ProfilePhoto photo = profilePhotoRepository.findFirstByUserIdOrderByDisplayOrderAscIdAsc(user.getId()).orElse(null);
		List<String> hobbies = hobbyNames(user.getId());
		if (!isProfileComplete(profile)
			|| matchingProfile == null
			|| matchingProfile.getJobIntro() == null
			|| matchingProfile.getJobIntro().isBlank()
			|| photo == null
			|| photo.getImageUrl() == null
			|| photo.getImageUrl().isBlank()
			|| hobbies.isEmpty()) {
			return null;
		}

		return new EligibleUserView(
			user.getId(),
			user.getName(),
			user.getGender().name(),
			age(user.getBirthDate()),
			user.getGrade() == null ? null : user.getGrade().name(),
			profile.getJob(),
			profile.getActivityRegion(),
			hobbies
		);
	}

	private EligibleUserView userSummary(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found."));
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		return new EligibleUserView(
			user.getId(),
			user.getName(),
			user.getGender() == null ? null : user.getGender().name(),
			age(user.getBirthDate()),
			user.getGrade() == null ? null : user.getGrade().name(),
			profile == null ? null : profile.getJob(),
			profile == null ? null : profile.getActivityRegion(),
			hobbyNames(userId)
		);
	}

	private List<String> hobbyNames(Long userId) {
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

	private boolean isProfileComplete(UserProfile profile) {
		return profile != null
			&& profile.getHeightCm() != null
			&& hasText(profile.getJob())
			&& hasText(profile.getEducation())
			&& hasText(profile.getActivityRegion())
			&& hasText(profile.getSmokingStatus())
			&& hasText(profile.getDrinkingStatus())
			&& hasText(profile.getReligion())
			&& hasText(profile.getMbti());
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private Integer age(LocalDate birthDate) {
		if (birthDate == null) {
			return null;
		}
		int age = Period.between(birthDate, LocalDate.now(DEFAULT_ZONE)).getYears();
		return age > 0 ? age : null;
	}

	private long countByStatus(List<MatchProposal> matches, MatchStatus status) {
		return matches.stream().filter(match -> match.getStatus() == status).count();
	}

	public record MatchingOperationsView(
		LocalDate date,
		MatchingOperationsSummary summary,
		List<MatchOperationView> matches,
		List<EligibleUserView> unmatchedUsers
	) {
	}

	public record MatchingOperationsSummary(
		int totalMatches,
		long pendingMatches,
		long acceptedMatches,
		long rejectedMatches,
		int unmatchedUsers
	) {
	}

	public record MatchOperationView(
		Long matchId,
		String matchType,
		String status,
		Instant matchedAt,
		Instant expiresAt,
		String userADecision,
		String userBDecision,
		String reason,
		EligibleUserView userA,
		EligibleUserView userB
	) {
	}

	public record EligibleUserView(
		Long userId,
		String name,
		String gender,
		Integer age,
		String grade,
		String job,
		String activityRegion,
		List<String> hobbies
	) {
	}

	public record ManualMatchCommand(Long userAId, Long userBId, String reason) {
	}

	public record ManualMatchScoreCommand(Long userAId, Long userBId) {
	}

	public record ManualMatchScorePreview(
		boolean eligible,
		int totalScore,
		int sharedHobbyCount,
		int hobbyScore,
		int smokingScore,
		int drinkingScore,
		int religionScore,
		int gradeScore,
		String message
	) {

		static ManualMatchScorePreview eligible(
			int totalScore,
			int sharedHobbyCount,
			int hobbyScore,
			int smokingScore,
			int drinkingScore,
			int religionScore,
			int gradeScore,
			String message
		) {
			return new ManualMatchScorePreview(
				true,
				totalScore,
				sharedHobbyCount,
				hobbyScore,
				smokingScore,
				drinkingScore,
				religionScore,
				gradeScore,
				message
			);
		}

		static ManualMatchScorePreview ineligible(String message) {
			return new ManualMatchScorePreview(false, 0, 0, 0, 0, 0, 0, 0, message);
		}
	}

	public record ManualMatchResult(Long matchId, Long userAId, Long userBId, String reason) {
	}
}
