package com.oao.backend.matching.repository;

import com.oao.backend.matching.domain.MatchProposal;
import com.oao.backend.matching.domain.MatchProposal.MatchType;
import com.oao.backend.matching.domain.MatchProposal.MatchStatus;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchProposalRepository extends JpaRepository<MatchProposal, Long> {

	List<MatchProposal> findByStatusAndUserAIdOrStatusAndUserBId(
		MatchStatus userAStatus,
		Long userAId,
		MatchStatus userBStatus,
		Long userBId
	);

	@Query("""
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
		@Param("statuses") Collection<MatchStatus> statuses
	);

	@Query("""
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
		@Param("matchType") MatchType matchType
	);

	List<MatchProposal> findByMatchedAtGreaterThanEqualAndMatchedAtLessThanOrderByMatchedAtDescIdDesc(
		Instant from,
		Instant to
	);
}
