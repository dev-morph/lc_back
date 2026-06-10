package com.oao.backend.notification.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.dev.service.DevToolGuardService;
import com.oao.backend.notification.service.AppNotificationService;
import com.oao.backend.notification.service.AppNotificationService.AppNotificationView;
import com.oao.backend.notification.service.AppNotificationService.UnreadCountView;
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
@RequestMapping("/notifications")
public class AppNotificationController {

	private final AppNotificationService notificationService;
	private final DevToolGuardService devToolGuardService;

	public AppNotificationController(
		AppNotificationService notificationService,
		DevToolGuardService devToolGuardService
	) {
		this.notificationService = notificationService;
		this.devToolGuardService = devToolGuardService;
	}

	@GetMapping
	ApiResponse<List<AppNotificationView>> notifications(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@RequestHeader(value = "X-Dev-Secret", required = false) String devSecret
	) {
		return ApiResponse.ok(notificationService.notifications(resolveUserId(principal, headerUserId, devSecret)));
	}

	@GetMapping("/unread-count")
	ApiResponse<UnreadCountView> unreadCount(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@RequestHeader(value = "X-Dev-Secret", required = false) String devSecret
	) {
		return ApiResponse.ok(notificationService.unreadCount(resolveUserId(principal, headerUserId, devSecret)));
	}

	@PostMapping("/{notificationId}/read")
	ApiResponse<AppNotificationView> markRead(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@RequestHeader(value = "X-Dev-Secret", required = false) String devSecret,
		@PathVariable Long notificationId
	) {
		return ApiResponse.ok(notificationService.markRead(notificationId, resolveUserId(principal, headerUserId, devSecret)));
	}

	@PostMapping("/read-all")
	ApiResponse<Void> markAllRead(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@RequestHeader(value = "X-Dev-Secret", required = false) String devSecret
	) {
		notificationService.markAllRead(resolveUserId(principal, headerUserId, devSecret));
		return ApiResponse.ok();
	}

	private Long resolveUserId(KakaoPrincipal principal, Long headerUserId, String devSecret) {
		if (headerUserId != null) {
			devToolGuardService.requireSecret(devSecret);
			return headerUserId;
		}
		if (principal == null) {
			throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
		}
		return principal.getUserId();
	}
}
