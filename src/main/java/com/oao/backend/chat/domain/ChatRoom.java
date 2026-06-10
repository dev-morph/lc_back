package com.oao.backend.chat.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_room")
public class ChatRoom extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long matchId;

	@Enumerated(EnumType.STRING)
	private ChatRoomStatus status = ChatRoomStatus.ACTIVE;

	protected ChatRoom() {
	}

	public static ChatRoom open(Long matchId) {
		ChatRoom room = new ChatRoom();
		room.matchId = matchId;
		return room;
	}

	public Long getId() {
		return id;
	}

	public Long getMatchId() {
		return matchId;
	}

	public ChatRoomStatus getStatus() {
		return status;
	}

	public enum ChatRoomStatus {
		ACTIVE,
		INACTIVE
	}
}
