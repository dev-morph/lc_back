package com.oao.backend.chat.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessage extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long chatRoomId;
	private Long senderUserId;
	private String messageType;
	private String content;
	private Instant readAt;
	private Instant deletedAt;

	protected ChatMessage() {
	}

	public static ChatMessage text(Long chatRoomId, Long senderUserId, String content) {
		ChatMessage message = new ChatMessage();
		message.chatRoomId = chatRoomId;
		message.senderUserId = senderUserId;
		message.messageType = "TEXT";
		message.content = content;
		return message;
	}

	public Long getId() {
		return id;
	}

	public Long getChatRoomId() {
		return chatRoomId;
	}

	public Long getSenderUserId() {
		return senderUserId;
	}

	public String getMessageType() {
		return messageType;
	}

	public String getContent() {
		return content;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}
}
