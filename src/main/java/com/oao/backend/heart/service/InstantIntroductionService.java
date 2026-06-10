package com.oao.backend.heart.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.heart.domain.InstantIntroductionConfig;
import com.oao.backend.heart.domain.HeartTransaction;
import com.oao.backend.heart.domain.HeartWallet;
import com.oao.backend.heart.domain.InstantIntroUsageWindow;
import com.oao.backend.heart.repository.HeartTransactionRepository;
import com.oao.backend.heart.repository.HeartWalletRepository;
import com.oao.backend.heart.repository.InstantIntroUsageWindowRepository;
import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.domain.MatchProposal.MatchStatus;
import com.oao.backend.matching.domain.MatchingScoreConfig;
import com.oao.backend.matching.repository.MatchProposalRepository;
import com.oao.backend.matching.service.MatchingCandidate;
import com.oao.backend.matching.service.MatchingCandidateService;
import com.oao.backend.matching.service.MatchingScore;
import com.oao.backend.matching.service.MatchingScoreCalculator;
import com.oao.backend.matching.service.MatchingScoreConfigService;
import com.oao.backend.notification.service.AppNotificationService;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.UserAccountRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstantIntroductionService {

	private static final Set<MatchStatus> ACTIVE_MATCH_STATUSES = Set.of(MatchStatus.PENDING, MatchStatus.ACCEPTED);

	private final int proposalValidDays;
	private final InstantIntroductionCostPolicy costPolicy;
	private final HeartWalletRepository heartWalletRepository;
	private final HeartTransactionRepository heartTransactionRepository;
	private final InstantIntroUsageWindowRepository usageWindowRepository;
	private final UserAccountRepository userAccountRepository;
	private final MatchProposalRepository matchProposalRepository;
	private final MatchingCandidateService matchingCandidateService;
	private final MatchingScoreConfigService matchingScoreConfigService;
	private final MatchingScoreCalculator matchingScoreCalculator;
	private final AppNotificationService notificationService;

	public InstantIntroductionService(
		@Value("${oao.matching.proposal-valid-days:3}") int proposalValidDays,
		InstantIntroductionCostPolicy costPolicy,
		HeartWalletRepository heartWalletRepository,
		HeartTransactionRepository heartTransactionRepository,
		InstantIntroUsageWindowRepository usageWindowRepository,
		UserAccountRepository userAccountRepository,
		MatchProposalRepository matchProposalRepository,
		MatchingCandidateService matchingCandidateService,
		MatchingScoreConfigService matchingScoreConfigService,
		MatchingScoreCalculator matchingScoreCalculator,
		AppNotificationService notificationService
	) {
		this.proposalValidDays = proposalValidDays;
		this.costPolicy = costPolicy;
		this.heartWalletRepository = heartWalletRepository;
		this.heartTransactionRepository = heartTransactionRepository;
		this.usageWindowRepository = usageWindowRepository;
		this.userAccountRepository = userAccountRepository;
		this.matchProposalRepository = matchProposalRepository;
		this.matchingCandidateService = matchingCandidateService;
		this.matchingScoreConfigService = matchingScoreConfigService;
		this.matchingScoreCalculator = matchingScoreCalculator;
		this.notificationService = notificationService;
	}

	@Transactional(readOnly = true)
	public InstantIntroductionCost currentCost(Long userId) {
		Instant now = Instant.now();
		InstantIntroductionConfig config = costPolicy.currentConfig();
		return usageWindowRepository.findTopByUserIdOrderByWindowStartedAtDesc(userId)
			.filter(window -> !window.isExpired(now))
			.map(window -> new InstantIntroductionCost(
				costPolicy.costFor(window.nextUsageCount(), config),
				window.nextUsageCount(),
				window.getWindowExpiresAt()
			))
			.orElseGet(() -> new InstantIntroductionCost(
				costPolicy.costFor(1, config),
				1,
				now.plusSeconds(config.getUsageWindowHours() * 60L * 60L)
			));
	}

	@Transactional
	public InstantIntroductionResult request(Long userId) {
		Instant now = Instant.now();
		InstantIntroductionConfig config = costPolicy.currentConfig();
		InstantIntroUsageWindow window = usageWindowRepository.findTopByUserIdOrderByWindowStartedAtDesc(userId)
			.filter(activeWindow -> !activeWindow.isExpired(now))
			.orElseGet(() -> usageWindowRepository.save(InstantIntroUsageWindow.start(userId, now, config.getUsageWindowHours())));

		int cost = costPolicy.costFor(window.nextUsageCount(), config);
		HeartWallet wallet = heartWalletRepository.findByUserId(userId)
			.orElseGet(() -> heartWalletRepository.save(HeartWallet.create(userId)));
		wallet.spend(cost);

		MatchingCandidate requester = toCandidateOrThrow(userId);
		ScoredCounterpart scoredCounterpart = findBestCounterpart(requester);
		if (scoredCounterpart == null) {
			throw new BusinessException(HttpStatus.CONFLICT, "No available match candidate.");
		}

		MatchingCandidate counterpart = scoredCounterpart.candidate();
		MatchingScore score = scoredCounterpart.score();
		String reason = matchingReason(score);
		MatchProposal proposal = matchProposalRepository.save(MatchProposal.createManual(
			requester.userId(),
			counterpart.userId(),
			userId,
			reason,
			now,
			now.plus(Math.max(1, proposalValidDays), ChronoUnit.DAYS)
		));

		window.recordUsage();
		heartTransactionRepository.save(HeartTransaction.spend(userId, cost, wallet.getBalance(), "INSTANT_INTRODUCTION", proposal.getId()));
		notificationService.notifyMatchArrived(proposal.getId(), requester.userId(), counterpart.userId());

		return new InstantIntroductionResult(cost, wallet.getBalance(), window.getUsageCount(), window.getWindowExpiresAt(), proposal.getId(), true);
	}

	private MatchingCandidate toCandidateOrThrow(Long userId) {
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found."));

		if (user.getStatus() != UserAccount.UserStatus.ACTIVE) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "User is not active for matching.");
		}
		if (user.getApprovalStatus() == UserAccount.ApprovalStatus.PENDING) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "User approval is pending.");
		}
		if (user.getApprovalStatus() == UserAccount.ApprovalStatus.REJECTED) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "User approval was rejected.");
		}

		MatchingCandidate candidate = toCandidateOrNull(user);
		if (candidate == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Profile is not ready for matching.");
		}
		return candidate;
	}

	private ScoredCounterpart findBestCounterpart(MatchingCandidate requester) {
		MatchingScoreConfig scoreConfig = matchingScoreConfigService.findOrDefault();
		return userAccountRepository.findAll().stream()
			.filter(user -> !user.getId().equals(requester.userId()))
			.map(this::toCandidateOrNull)
			.filter(candidate -> candidate != null)
			.filter(candidate -> candidate.gender() != requester.gender())
			.filter(candidate -> !matchProposalRepository.existsActivePair(requester.userId(), candidate.userId(), ACTIVE_MATCH_STATUSES))
			.map(candidate -> new ScoredCounterpart(candidate, matchingScoreCalculator.calculate(requester, candidate, scoreConfig)))
			.max(Comparator
				.comparingInt((ScoredCounterpart counterpart) -> counterpart.score().totalScore())
				.thenComparingInt(counterpart -> counterpart.score().sharedHobbyCount())
				.thenComparingInt(counterpart -> -counterpart.candidate().autoMatchCount())
				.thenComparing(counterpart -> counterpart.candidate().lastAutoMatchedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(counterpart -> counterpart.candidate().userId(), Comparator.reverseOrder()))
			.orElse(null);
	}

	private MatchingCandidate toCandidateOrNull(UserAccount user) {
		return matchingCandidateService.toCandidate(user).orElse(null);
	}

	private String matchingReason(MatchingScore score) {
		if (score.sharedHobbyCount() > 0) {
			return "공통 관심사와 생활 정보를 바탕으로 바로 추천했어요.";
		}
		return "프로필 생활 정보를 바탕으로 바로 추천했어요.";
	}

	private record ScoredCounterpart(MatchingCandidate candidate, MatchingScore score) {
	}

	public record InstantIntroductionCost(int requiredHearts, int nextUsageCount, Instant windowExpiresAt) {
	}

	public record InstantIntroductionResult(
		int spentHearts,
		int remainingHearts,
		int usageCount,
		Instant windowExpiresAt,
		Long matchId,
		boolean matched
	) {
	}
}
