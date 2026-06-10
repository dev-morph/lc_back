package com.oao.backend.chat.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_room_member_state")
public class ChatRoomMemberState extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long chatRoomId;
	private Long userId;
	private Instant leftAt;

	protected ChatRoomMemberState() {
	}

	public static ChatRoomMemberState leave(Long chatRoomId, Long userId) {
		ChatRoomMemberState state = new ChatRoomMemberState();
		state.chatRoomId = chatRoomId;
		state.userId = userId;
		state.leftAt = Instant.now();
		return state;
	}

	public void leave() {
		if (leftAt == null) {
			leftAt = Instant.now();
		}
	}

	public Long getChatRoomId() {
		return chatRoomId;
	}

	public Long getUserId() {
		return userId;
	}

	public Instant getLeftAt() {
		return leftAt;
	}
}
