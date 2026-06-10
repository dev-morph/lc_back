package com.oao.backend.meeting.api;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.meeting.service.MeetingService;
import com.oao.backend.meeting.service.MeetingService.MeetingView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/meetings")
public class MeetingController {

	private final MeetingService meetingService;

	public MeetingController(MeetingService meetingService) {
		this.meetingService = meetingService;
	}

	@GetMapping
	ApiResponse<List<MeetingView>> meetings(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
		@RequestParam(value = "tab", required = false) String tab
	) {
		return ApiResponse.ok(meetingService.findMeetings(resolveUserId(principal, headerUserId), tab));
	}

	@GetMapping("/{meetingId}")
	ApiResponse<MeetingView> meeting(
		@PathVariable Long meetingId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(meetingService.findMeeting(meetingId, resolveUserId(principal, headerUserId)));
	}

	@PostMapping("/{meetingId}/applications")
	ApiResponse<MeetingView> apply(
		@PathVariable Long meetingId,
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestHeader(value = "X-User-Id", required = false) Long headerUserId
	) {
		return ApiResponse.ok(meetingService.apply(meetingId, resolveUserId(principal, headerUserId)));
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
