package com.oao.backend.matching.service;

import com.oao.backend.chat.domain.ChatRoom;
import com.oao.backend.chat.repository.ChatRoomRepository;
import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.domain.MatchProposal.MatchStatus;
import com.oao.backend.matching.repository.MatchProposalRepository;
import com.oao.backend.notification.service.AppNotificationService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Call after acquiring UserLocks.pair; all connection paths use the same lock order. */
@Service
public class MatchConnectionService {
  private final MatchProposalRepository matches;
  private final ChatRoomRepository rooms;
  private final AppNotificationService notifications;
  private final MatchingPolicyService policy;

  public MatchConnectionService(MatchProposalRepository matches, ChatRoomRepository rooms,
      AppNotificationService notifications, MatchingPolicyService policy) {
    this.matches = matches; this.rooms = rooms; this.notifications = notifications; this.policy = policy;
  }

  public ChatRoom activeRoom(Long a, Long b) {
    for (var match : matches.findPairLocked(a, b)) {
      if (!match.isAccepted()) continue;
      var room = rooms.findByMatchId(match.getId()).orElse(null);
      if (room != null && room.getStatus() == ChatRoom.ChatRoomStatus.ACTIVE) return room;
    }
    return null;
  }

  @Transactional
  public Connection connect(Long a, Long b, String reason) {
    policy.requirePair(a, b);
    policy.requireConnectionAge(a, b);
    var pair = matches.findPairLocked(a, b);
    ChatRoom active = activeRoom(a, b);
    if (active != null) {
      pair.stream().filter(m -> m.getStatus() == MatchStatus.PENDING).forEach(MatchProposal::close);
      return new Connection(active.getMatchId(), active.getId(), false);
    }
    Instant now = Instant.now();
    MatchProposal selected = pair.stream().filter(MatchProposal::isAccepted).findFirst().orElse(null);
    if (selected == null) selected = pair.stream()
        .filter(m -> m.getStatus() == MatchStatus.PENDING && (m.getExpiresAt() == null || m.getExpiresAt().isAfter(now)))
        .filter(m -> m.getUserADecision() != MatchProposal.MatchDecision.REJECTED && m.getUserBDecision() != MatchProposal.MatchDecision.REJECTED)
        .findFirst().orElse(null);
    if (selected == null) {
      selected = matches.save(MatchProposal.createAcceptedInterest(a, b, a, reason, now));
    } else if (!selected.isAccepted()) {
      selected.accept(a);
      selected.accept(b);
    }
    Long matchId = selected.getId();
    pair.stream().filter(m -> !m.getId().equals(matchId) && m.getStatus() == MatchStatus.PENDING)
        .forEach(MatchProposal::close);
    ChatRoom room = rooms.findByMatchId(matchId).orElseGet(() -> rooms.save(ChatRoom.open(matchId)));
    notifications.notifyMatchCompleted(matchId, a, b);
    return new Connection(matchId, room.getId(), true);
  }

  public record Connection(Long matchId, Long roomId, boolean created) {}
}
