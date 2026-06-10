package com.oao.backend.chat.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.chat.service.ChatService;
import com.oao.backend.chat.service.ChatService.ChatMessageView;
import com.oao.backend.common.BusinessException;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

	private final ChatService chatService;
	private final SimpMessagingTemplate messagingTemplate;

	public ChatWebSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
		this.chatService = chatService;
		this.messagingTemplate = messagingTemplate;
	}

	@MessageMapping("/chat.rooms.{roomId}.send")
	public void send(
		@DestinationVariable Long roomId,
		@Payload SendChatMessageRequest request,
		Principal principal,
		SimpMessageHeaderAccessor headers
	) {
		Long senderUserId = resolveUserId(principal, headers);
		ChatMessageView message = chatService.sendTextMessage(roomId, senderUserId, request.content());
		messagingTemplate.convertAndSend("/topic/chat.rooms." + roomId, message);
	}

	private Long resolveUserId(Principal principal, SimpMessageHeaderAccessor headers) {
		if (principal instanceof Authentication authentication
			&& authentication.getPrincipal() instanceof KakaoPrincipal kakaoPrincipal) {
			return kakaoPrincipal.getUserId();
		}
		String headerUserId = headers.getFirstNativeHeader("X-User-Id");
		if (headerUserId != null && !headerUserId.isBlank()) {
			try {
				return Long.parseLong(headerUserId);
			} catch (NumberFormatException ignored) {
				throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
			}
		}
		throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
	}

	record SendChatMessageRequest(String content) {
	}
}
