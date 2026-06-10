package com.oao.backend.matching.domain;

import com.oao.backend.common.BaseTimeEntity;
import com.oao.backend.common.BusinessException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "match_proposal")
public class MatchProposal extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "match_type")
	private MatchType matchType;

	@Column(name = "user_a_id")
	private Long userAId;

	@Column(name = "user_b_id")
	private Long userBId;

	@Column(name = "requested_by_user_id")
	private Long requestedByUserId;

	@Enumerated(EnumType.STRING)
	private MatchStatus status = MatchStatus.PENDING;

	@Column(name = "matched_reason")
	private String matchedReason;

	@Column(name = "is_s_grade_guaranteed")
	private boolean sGradeGuaranteed;

	@Column(name = "matched_at")
	private Instant matchedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_a_decision")
	private MatchDecision userADecision = MatchDecision.PENDING;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_b_decision")
	private MatchDecision userBDecision = MatchDecision.PENDING;

	@Column(name = "user_a_decided_at")
	private Instant userADecidedAt;

	@Column(name = "user_b_decided_at")
	private Instant userBDecidedAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "rejected_at")
	private Instant rejectedAt;

	@Column(name = "expired_at")
	private Instant expiredAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected MatchProposal() {
	}

	public static MatchProposal createAuto(
		Long userAId,
		Long userBId,
		String matchedReason,
		boolean sGradeGuaranteed,
		Instant matchedAt,
		Instant expiresAt
	) {
		MatchProposal proposal = new MatchProposal();
		proposal.matchType = MatchType.AUTO;
		proposal.userAId = userAId;
		proposal.userBId = userBId;
		proposal.status = MatchStatus.PENDING;
		proposal.matchedReason = matchedReason;
		proposal.sGradeGuaranteed = sGradeGuaranteed;
		proposal.matchedAt = matchedAt;
		proposal.expiresAt = expiresAt;
		proposal.userADecision = MatchDecision.PENDING;
		proposal.userBDecision = MatchDecision.PENDING;
		return proposal;
	}

	public static MatchProposal createManual(
		Long userAId,
		Long userBId,
		Long requestedByUserId,
		String matchedReason,
		Instant matchedAt,
		Instant expiresAt
	) {
		MatchProposal proposal = new MatchProposal();
		proposal.matchType = MatchType.PREMIUM_MANUAL;
		proposal.userAId = userAId;
		proposal.userBId = userBId;
		proposal.requestedByUserId = requestedByUserId;
		proposal.status = MatchStatus.PENDING;
		proposal.matchedReason = matchedReason;
		proposal.sGradeGuaranteed = false;
		proposal.matchedAt = matchedAt;
		proposal.expiresAt = expiresAt;
		proposal.userADecision = MatchDecision.PENDING;
		proposal.userBDecision = MatchDecision.PENDING;
		return proposal;
	}

	public static MatchProposal createAcceptedInterest(
		Long userAId,
		Long userBId,
		Long requestedByUserId,
		String matchedReason,
		Instant matchedAt
	) {
		MatchProposal proposal = new MatchProposal();
		proposal.matchType = MatchType.INTEREST;
		proposal.userAId = userAId;
		proposal.userBId = userBId;
		proposal.requestedByUserId = requestedByUserId;
		proposal.status = MatchStatus.ACCEPTED;
		proposal.matchedReason = matchedReason;
		proposal.sGradeGuaranteed = false;
		proposal.matchedAt = matchedAt;
		proposal.expiresAt = null;
		proposal.userADecision = MatchDecision.ACCEPTED;
		proposal.userBDecision = MatchDecision.ACCEPTED;
		proposal.userADecidedAt = matchedAt;
		proposal.userBDecidedAt = matchedAt;
		proposal.acceptedAt = matchedAt;
		return proposal;
	}

	public void accept(Long userId) {
		ensurePending();
		decide(userId, MatchDecision.ACCEPTED);
		if (userADecision == MatchDecision.ACCEPTED && userBDecision == MatchDecision.ACCEPTED) {
			this.status = MatchStatus.ACCEPTED;
			this.acceptedAt = Instant.now();
		}
	}

	public void reject(Long userId) {
		ensurePending();
		decide(userId, MatchDecision.REJECTED);
		this.status = MatchStatus.REJECTED;
		this.rejectedAt = Instant.now();
	}

	public void close() {
		this.status = MatchStatus.CLOSED;
		this.closedAt = Instant.now();
	}

	public boolean isAccepted() {
		return status == MatchStatus.ACCEPTED;
	}

	public Long getId() {
		return id;
	}

	public MatchStatus getStatus() {
		return status;
	}

	public MatchType getMatchType() {
		return matchType;
	}

	public Long getUserAId() {
		return userAId;
	}

	public Long getUserBId() {
		return userBId;
	}

	public String getMatchedReason() {
		return matchedReason;
	}

	public boolean isSGradeGuaranteed() {
		return sGradeGuaranteed;
	}

	public Instant getMatchedAt() {
		return matchedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public MatchDecision getUserADecision() {
		return userADecision;
	}

	public MatchDecision getUserBDecision() {
		return userBDecision;
	}

	private void ensurePending() {
		if (status != MatchStatus.PENDING) {
			throw new BusinessException(HttpStatus.CONFLICT, "Match is not waiting for a decision.");
		}
	}

	private void decide(Long userId, MatchDecision decision) {
		Instant now = Instant.now();
		if (userAId.equals(userId)) {
			this.userADecision = decision;
			this.userADecidedAt = now;
			return;
		}
		if (userBId.equals(userId)) {
			this.userBDecision = decision;
			this.userBDecidedAt = now;
			return;
		}
		throw new BusinessException(HttpStatus.FORBIDDEN, "User is not a participant of this match.");
	}

	public enum MatchType {
		AUTO,
		INSTANT,
		PREMIUM_MANUAL,
		INTEREST
	}

	public enum MatchStatus {
		PENDING,
		ACCEPTED,
		REJECTED,
		EXPIRED,
		CLOSED
	}

	public enum MatchDecision {
		PENDING,
		ACCEPTED,
		REJECTED
	}
}
