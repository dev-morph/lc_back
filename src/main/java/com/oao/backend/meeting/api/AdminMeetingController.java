package com.oao.backend.meeting.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.ApiResponse;
import com.oao.backend.common.BusinessException;
import com.oao.backend.meeting.domain.MeetingApplication.MeetingApplicationStatus;
import com.oao.backend.meeting.domain.MeetingApplication.MeetingPaymentStatus;
import com.oao.backend.meeting.service.AdminMeetingService;
import com.oao.backend.meeting.service.AdminMeetingService.AdminMeetingApplicationView;
import com.oao.backend.meeting.service.AdminMeetingService.AdminMeetingView;
import com.oao.backend.meeting.service.AdminMeetingService.CreateMeetingCommand;
import com.oao.backend.user.domain.AdminUser;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/meetings")
public class AdminMeetingController {

	private final AdminMeetingService adminMeetingService;
	private final AdminAccessService adminAccessService;

	public AdminMeetingController(AdminMeetingService adminMeetingService, AdminAccessService adminAccessService) {
		this.adminMeetingService = adminMeetingService;
		this.adminAccessService = adminAccessService;
	}

	@GetMapping
	ApiResponse<List<AdminMeetingView>> meetings(@AuthenticationPrincipal KakaoPrincipal principal) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminMeetingService.findMeetings());
	}

	@PostMapping
	ApiResponse<AdminMeetingView> createMeeting(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@RequestParam("title") String title,
		@RequestParam("description") String description,
		@RequestParam("eventDateTime") LocalDateTime eventDateTime,
		@RequestParam("priceAmount") Integer priceAmount,
		@RequestParam("capacity") Integer capacity,
		@RequestParam("image") MultipartFile image
	) {
		AdminUser admin = adminAccessService.requireActiveAdmin(principal);
		CreateMeetingCommand command = new CreateMeetingCommand(
			title,
			description,
			eventDateTime,
			priceAmount,
			capacity
		);
		return ApiResponse.ok(adminMeetingService.createMeeting(command, image, admin.getId()));
	}

	@GetMapping("/{meetingId}/applications")
	ApiResponse<List<AdminMeetingApplicationView>> applications(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long meetingId
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminMeetingService.findApplications(meetingId));
	}

	@PatchMapping("/{meetingId}/applications/{applicationId}/status")
	ApiResponse<AdminMeetingApplicationView> changeApplicationStatus(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long meetingId,
		@PathVariable Long applicationId,
		@RequestBody ChangeApplicationStatusRequest request
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminMeetingService.changeApplicationStatus(
			meetingId,
			applicationId,
			parseApplicationStatus(request.applicationStatus()),
			request.adminNote()
		));
	}

	@PatchMapping("/{meetingId}/applications/{applicationId}/payment")
	ApiResponse<AdminMeetingApplicationView> changePaymentStatus(
		@AuthenticationPrincipal KakaoPrincipal principal,
		@PathVariable Long meetingId,
		@PathVariable Long applicationId,
		@RequestBody ChangePaymentStatusRequest request
	) {
		adminAccessService.requireActiveAdmin(principal);
		return ApiResponse.ok(adminMeetingService.changePaymentStatus(
			meetingId,
			applicationId,
			parsePaymentStatus(request.paymentStatus()),
			request.adminNote()
		));
	}

	private MeetingApplicationStatus parseApplicationStatus(String value) {
		try {
			return MeetingApplicationStatus.valueOf(value == null ? "" : value.trim().toUpperCase());
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting application status is invalid.");
		}
	}

	private MeetingPaymentStatus parsePaymentStatus(String value) {
		try {
			return MeetingPaymentStatus.valueOf(value == null ? "" : value.trim().toUpperCase());
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting payment status is invalid.");
		}
	}

	record ChangeApplicationStatusRequest(String applicationStatus, String adminNote) {
	}

	record ChangePaymentStatusRequest(String paymentStatus, String adminNote) {
	}
}
