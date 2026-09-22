package com.oao.backend.chat.service;

import com.oao.backend.common.DbRows;
import com.oao.backend.matching.service.MatchingPolicyService;
import org.springframework.stereotype.Service;

/** Read-only availability for list/detail CTAs; chat endpoints still enforce access. */
@Service
public class ChatAvailabilityService {
  private final DbRows db;
  private final MatchingPolicyService policy;

  public ChatAvailabilityService(DbRows db, MatchingPolicyService policy) {
    this.db = db;
    this.policy = policy;
  }

  public boolean available(Long matchId, Long viewer, Long counterpart) {
    if (matchId == null || !policy.pairAllowed(viewer, counterpart)) return false;
    return db.count("select count(*) from chat_room r join match_proposal m on m.id=r.match_id"
        + " where m.id=? and m.status='ACCEPTED' and r.status='ACTIVE'"
        + " and (m.user_a_id=? or m.user_b_id=?)"
        + " and (select count(*) from user_account where id in (?,?) and status='ACTIVE')=2"
        + " and not exists (select 1 from chat_room_member_state s where s.chat_room_id=r.id"
        + " and s.user_id=? and s.left_at is not null)",
        matchId, viewer, viewer, viewer, counterpart, viewer) > 0;
  }
}
