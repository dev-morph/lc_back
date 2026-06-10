package com.oao.backend.matching.service;

import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.domain.MatchProposal.MatchStatus;
import com.oao.backend.matching.domain.MatchProposal.MatchType;
import com.oao.backend.matching.domain.MatchingProfile;
import com.oao.backend.matching.domain.MatchingScoreConfig;
import com.oao.backend.matching.repository.MatchProposalRepository;
import com.oao.backend.matching.repository.MatchingProfileRepository;
import com.oao.backend.notification.service.AppNotificationService;
import com.oao.backend.user.repository.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutoMatchingService {

	private static final Set<MatchStatus> ACTIVE_MATCH_STATUSES = Set.of(MatchStatus.PENDING, MatchStatus.ACCEPTED);

	private final int proposalValidDays;
	private final UserAccountRepository userAccountRepository;
	private final MatchingProfileRepository matchingProfileRepository;
	private final MatchProposalRepository matchProposalRepository;
	private final MatchingCandidateService matchingCandidateService;
	private final MatchingScoreConfigService matchingScoreConfigService;
	private final MatchingScoreCalculator matchingScoreCalculator;
	private final AppNotificationService notificationService;

	public AutoMatchingService(
		@Value("${oao.matching.proposal-valid-days:3}") int proposalValidDays,
		UserAccountRepository userAccountRepository,
		MatchingProfileRepository matchingProfileRepository,
		MatchProposalRepository matchProposalRepository,
		MatchingCandidateService matchingCandidateService,
		MatchingScoreConfigService matchingScoreConfigService,
		MatchingScoreCalculator matchingScoreCalculator,
		AppNotificationService notificationService
	) {
		this.proposalValidDays = proposalValidDays;
		this.userAccountRepository = userAccountRepository;
		this.matchingProfileRepository = matchingProfileRepository;
		this.matchProposalRepository = matchProposalRepository;
		this.matchingCandidateService = matchingCandidateService;
		this.matchingScoreConfigService = matchingScoreConfigService;
		this.matchingScoreCalculator = matchingScoreCalculator;
		this.notificationService = notificationService;
	}

	@Transactional
	public AutoMatchingResult runAutoMatching() {
		MatchingScoreConfig scoreConfig = matchingScoreConfigService.findOrDefault();
		List<MatchingCandidate> candidates = userAccountRepository.findAll().stream()
			.map(matchingCandidateService::toCandidate)
			.flatMap(optional -> optional.stream())
			.sorted(Comparator.comparing(MatchingCandidate::userId))
			.toList();

		List<MatchingCandidate> unmatched = new ArrayList<>(candidates);
		List<CreatedMatchView> createdMatches = new ArrayList<>();
		Instant now = Instant.now();
		Instant expiresAt = now.plus(Math.max(1, proposalValidDays), ChronoUnit.DAYS);

		while (!unmatched.isEmpty()) {
			MatchingCandidate base = unmatched.remove(0);
			ScoredCounterpart scoredCounterpart = findBestCounterpart(base, unmatched, scoreConfig);
			if (scoredCounterpart == null) {
				continue;
			}

			MatchingCandidate counterpart = scoredCounterpart.candidate();
			MatchingScore score = scoredCounterpart.score();
			unmatched.remove(counterpart);
			String reason = matchingReason(score);

			MatchProposal proposal = matchProposalRepository.save(MatchProposal.createAuto(
				base.userId(),
				counterpart.userId(),
				reason,
				false,
				now,
				expiresAt
			));
			recordAutoMatch(base.userId());
			recordAutoMatch(counterpart.userId());
			notificationService.notifyMatchArrived(proposal.getId(), base.userId(), counterpart.userId());

			createdMatches.add(new CreatedMatchView(
				proposal.getId(),
				base.userId(),
				counterpart.userId(),
				score.totalScore(),
				score.sharedHobbyCount(),
				reason
			));
		}

		return new AutoMatchingResult(createdMatches.size(), createdMatches);
	}

	private ScoredCounterpart findBestCounterpart(MatchingCandidate base, List<MatchingCandidate> candidates, MatchingScoreConfig scoreConfig) {
		return candidates.stream()
			.filter(candidate -> candidate.gender() != base.gender())
			.filter(candidate -> !hasActivePair(base.userId(), candidate.userId()))
			.filter(candidate -> scoreConfig.isAllowPreviousAutoMatch() || !hasPreviousAutoPair(base.userId(), candidate.userId()))
			.map(candidate -> new ScoredCounterpart(candidate, matchingScoreCalculator.calculate(base, candidate, scoreConfig)))
			.max(Comparator
				.comparingInt((ScoredCounterpart counterpart) -> counterpart.score().totalScore())
				.thenComparingInt(counterpart -> counterpart.score().sharedHobbyCount())
				.thenComparingInt(counterpart -> -counterpart.candidate().autoMatchCount())
				.thenComparing(counterpart -> counterpart.candidate().lastAutoMatchedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(counterpart -> counterpart.candidate().userId(), Comparator.reverseOrder()))
			.orElse(null);
	}

	private boolean hasActivePair(Long userAId, Long userBId) {
		return matchProposalRepository.existsActivePair(userAId, userBId, ACTIVE_MATCH_STATUSES);
	}

	private boolean hasPreviousAutoPair(Long userAId, Long userBId) {
		return matchProposalRepository.existsPairByType(userAId, userBId, MatchType.AUTO);
	}

	private String matchingReason(MatchingScore score) {
		if (score.sharedHobbyCount() > 0) {
			return "공통 관심사와 생활 정보를 바탕으로 추천했어요.";
		}
		return "프로필 생활 정보를 바탕으로 추천했어요.";
	}

	private void recordAutoMatch(Long userId) {
		MatchingProfile matchingProfile = matchingProfileRepository.findByUserId(userId)
			.orElseGet(() -> matchingProfileRepository.save(MatchingProfile.create(userId)));
		matchingProfile.recordAutoMatch(false);
	}

	private record ScoredCounterpart(MatchingCandidate candidate, MatchingScore score) {
	}

	public record AutoMatchingResult(int createdCount, List<CreatedMatchView> createdMatches) {
	}

	public record CreatedMatchView(
		Long matchId,
		Long userAId,
		Long userBId,
		int score,
		int sharedHobbyCount,
		String reason
	) {
	}
}
