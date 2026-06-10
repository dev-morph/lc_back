package com.oao.backend.chat.repository;

import com.oao.backend.chat.domain.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	List<ChatMessage> findByChatRoomIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long chatRoomId);

	Optional<ChatMessage> findFirstByChatRoomIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long chatRoomId);
}
