package com.oao.backend.matching.repository;

import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.domain.MatchProposal.MatchStatus;
import com.oao.backend.matching.domain.MatchProposal.MatchType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, Long> {
  @Query("select m from MatchProposal m where (m.userAId=:a and m.userBId=:b) or (m.userAId=:b and m.userBId=:a)")
  List<MatchProposal> findPair(@Param("a") Long a, @Param("b") Long b);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from MatchProposal m where ((m.userAId=:a and m.userBId=:b) or (m.userAId=:b and m.userBId=:a)) order by m.id")
  List<MatchProposal> findPairLocked(@Param("a") Long a, @Param("b") Long b);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from MatchProposal m where m.id=:id")
  java.util.Optional<MatchProposal> findLockedById(@Param("id") Long id);

  List<MatchProposal> findByStatusAndUserAIdOrStatusAndUserBId(
      MatchStatus userAStatus, Long userAId, MatchStatus userBStatus, Long userBId);

  @Query(
      """
      select count(match) > 0
      from MatchProposal match
      where match.status in :statuses
        and (
          (match.userAId = :userAId and match.userBId = :userBId)
          or (match.userAId = :userBId and match.userBId = :userAId)
        )
      """)
  boolean existsActivePair(
      @Param("userAId") Long userAId,
      @Param("userBId") Long userBId,
      @Param("statuses") Collection<MatchStatus> statuses);

  @Query(
      """
      select count(match) > 0
      from MatchProposal match
      where match.matchType = :matchType
        and (
          (match.userAId = :userAId and match.userBId = :userBId)
          or (match.userAId = :userBId and match.userBId = :userAId)
        )
      """)
  boolean existsPairByType(
      @Param("userAId") Long userAId,
      @Param("userBId") Long userBId,
      @Param("matchType") MatchType matchType);

  List<MatchProposal> findByMatchedAtGreaterThanEqualAndMatchedAtLessThanOrderByMatchedAtDescIdDesc(
      Instant from, Instant to);
}
