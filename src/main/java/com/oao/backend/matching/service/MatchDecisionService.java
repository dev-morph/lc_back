package com.oao.backend.matching.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.repository.MatchProposalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchDecisionService {
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
  private final com.oao.backend.matching.service.MatchingPolicyService matchingPolicy;

  private final MatchProposalRepository matchProposalRepository;
  private final com.oao.backend.common.UserLocks userLocks;
  private final MatchConnectionService connections;

  public MatchDecisionService(
      com.oao.backend.matching.service.MatchingPolicyService matchingPolicy,
      MatchProposalRepository matchProposalRepository,
      com.oao.backend.common.UserLocks userLocks,
      MatchConnectionService connections) {
    this.matchingPolicy = matchingPolicy;
    this.matchProposalRepository = matchProposalRepository;
    this.userLocks = userLocks;
    this.connections = connections;
  }

  @Transactional
  public MatchDecisionResult accept(Long matchId, Long userId) {
    lockPair(matchId, userId);
    MatchProposal match = findMatch(matchId);
    matchingPolicy.requirePair(match.getUserAId(), match.getUserBId());
    matchingPolicy.requireConnectionAge(match.getUserAId(), match.getUserBId());
    if (match.isAccepted()) {
      var connected = connections.connect(match.getUserAId(), match.getUserBId(), "서로 수락했어요.");
      return new MatchDecisionResult(connected.matchId(), "ACCEPTED", connected.created());
    }
    match.accept(userId);
    boolean chatRoomCreated = false;
    if (match.isAccepted()) {
      chatRoomCreated = connections.connect(match.getUserAId(), match.getUserBId(), "서로 수락했어요.").created();
    }
    return new MatchDecisionResult(match.getId(), match.getStatus().name(), chatRoomCreated);
  }

  @Transactional
  public MatchDecisionResult reject(Long matchId, Long userId) {
    lockPair(matchId, userId);
    MatchProposal match = findMatch(matchId);
    matchingPolicy.requirePair(match.getUserAId(), match.getUserBId());
    match.reject(userId);
    return new MatchDecisionResult(match.getId(), match.getStatus().name(), false);
  }

  private MatchProposal findMatch(Long matchId) {
    var match = matchProposalRepository
        .findLockedById(matchId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Match not found."));
    entityManager.refresh(match, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    return match;
  }

  private void lockPair(Long matchId, Long userId) {
    var match = matchProposalRepository.findById(matchId)
        .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Match not found."));
    if (!userId.equals(match.getUserAId()) && !userId.equals(match.getUserBId()))
      throw new BusinessException(HttpStatus.FORBIDDEN, "User is not a participant of this match.");
    userLocks.pair(match.getUserAId(), match.getUserBId());
  }

  public record MatchDecisionResult(Long matchId, String status, boolean chatRoomCreated) {}
}
