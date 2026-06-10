package com.oao.backend.chat.repository;

import com.oao.backend.chat.domain.ChatRoomMemberState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberStateRepository extends JpaRepository<ChatRoomMemberState, Long> {

	Optional<ChatRoomMemberState> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

	boolean existsByChatRoomIdAndUserIdAndLeftAtIsNotNull(Long chatRoomId, Long userId);
}
