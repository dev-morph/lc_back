package com.oao.backend.chat.repository;

import com.oao.backend.chat.domain.ChatRoom;
import com.oao.backend.chat.domain.ChatRoom.ChatRoomStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	boolean existsByMatchId(Long matchId);

	Optional<ChatRoom> findByMatchId(Long matchId);

	@Query("""
		select room
		from ChatRoom room
		join MatchProposal match on room.matchId = match.id
		where room.status = :roomStatus
			and match.status = com.oao.backend.matching.domain.MatchProposal.MatchStatus.ACCEPTED
			and (match.userAId = :userId or match.userBId = :userId)
		order by room.updatedAt desc, room.id desc
		""")
	List<ChatRoom> findActiveRoomsByParticipant(
		@Param("userId") Long userId,
		@Param("roomStatus") ChatRoomStatus roomStatus
	);
}
