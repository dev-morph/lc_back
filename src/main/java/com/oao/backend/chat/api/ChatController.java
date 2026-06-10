package com.oao.backend.chat.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.chat.service.ChatService;
import com.oao.backend.chat.service.ChatService.ChatMessageView;
import com.oao.backend.chat.service.ChatService.ChatRoomDetailView;
import com.oao.backend.chat.service.ChatService.ChatRoomSummaryView;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat/rooms")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@GetMapping
	ApiResponse<List<ChatRoomSummaryView>> rooms(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(chatService.rooms(resolveUserId(principal, headerUserId)));
	}

	@GetMapping("/by-match/{matchId}")
	ApiResponse<ChatRoomDetailView> roomByMatch(
		@PathVariable Long matchId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(chatService.findOrOpenByMatch(matchId, resolveUserId(principal, headerUserId)));
	}

	@GetMapping("/{roomId}")
	ApiResponse<ChatRoomDetailView> room(
		@PathVariable Long roomId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(chatService.room(roomId, resolveUserId(principal, headerUserId)));
	}

	@GetMapping("/{roomId}/messages")
	ApiResponse<List<ChatMessageView>> messages(
		@PathVariable Long roomId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(chatService.messages(roomId, resolveUserId(principal, headerUserId)));
	}

	@PostMapping("/{roomId}/leave")
	ApiResponse<Void> leave(
		@PathVariable Long roomId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		chatService.leave(roomId, resolveUserId(principal, headerUserId));
		return ApiResponse.ok();
	}

	private Long resolveUserId(KakaoPrincipal principal, Long headerUserId) {
		if (principal != null) {
			return principal.getUserId();
		}
		if (headerUserId != null) {
			return headerUserId;
		}
		throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
	}
}
