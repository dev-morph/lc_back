package com.oao.backend.interest.service;

import com.oao.backend.interest.domain.UserInterest;
import com.oao.backend.matching.domain.MatchProposal.MatchStatus;
import com.oao.backend.matching.repository.MatchProposalRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Read and write paths agree about whether a previous interest is still actionable. */
@Component
public class InterestConsentPolicy {
  private final MatchProposalRepository matches;

  public InterestConsentPolicy(MatchProposalRepository matches) { this.matches = matches; }

  public boolean active(UserInterest interest) {
    if (interest.getStatus() != UserInterest.InterestStatus.ACTIVE
        || interest.getExpressDecision() == UserInterest.ExpressDecision.REJECTED
        || interest.isChatRoomCreated()) return false;
    Instant actionAt = interest.getUpdatedAt();
    if (actionAt == null) return false;
    return matches.findPair(interest.getSenderUserId(), interest.getReceiverUserId()).stream()
        .noneMatch(match -> {
          Instant endedAt = null;
          if (match.getStatus() == MatchStatus.EXPIRED
              || (match.getStatus() == MatchStatus.PENDING && match.getExpiresAt() != null
                  && !match.getExpiresAt().isAfter(Instant.now()))) endedAt = match.getExpiresAt();
          else if (match.getStatus() == MatchStatus.REJECTED || match.getStatus() == MatchStatus.CLOSED)
            endedAt = match.getUpdatedAt();
          return endedAt != null && !endedAt.isBefore(actionAt);
        });
  }
}
