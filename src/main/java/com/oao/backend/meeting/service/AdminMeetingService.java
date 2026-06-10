package com.oao.backend.meeting.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.config.UploadProperties;
import com.oao.backend.meeting.domain.MeetingApplication;
import com.oao.backend.meeting.domain.MeetingApplication.MeetingApplicationStatus;
import com.oao.backend.meeting.domain.MeetingApplication.MeetingPaymentStatus;
import com.oao.backend.meeting.domain.MeetingEvent;
import com.oao.backend.meeting.repository.MeetingApplicationRepository;
import com.oao.backend.meeting.repository.MeetingEventRepository;
import com.oao.backend.user.domain.ProfilePhoto;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.domain.UserProfile;
import com.oao.backend.user.repository.ProfilePhotoRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import com.oao.backend.user.repository.UserProfileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminMeetingService {

	private static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024L * 1024L;
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final MeetingEventRepository meetingEventRepository;
	private final MeetingApplicationRepository meetingApplicationRepository;
	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final ProfilePhotoRepository profilePhotoRepository;
	private final UploadProperties uploadProperties;

	public AdminMeetingService(
		MeetingEventRepository meetingEventRepository,
		MeetingApplicationRepository meetingApplicationRepository,
		UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		ProfilePhotoRepository profilePhotoRepository,
		UploadProperties uploadProperties
	) {
		this.meetingEventRepository = meetingEventRepository;
		this.meetingApplicationRepository = meetingApplicationRepository;
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.profilePhotoRepository = profilePhotoRepository;
		this.uploadProperties = uploadProperties;
	}

	@Transactional(readOnly = true)
	public List<AdminMeetingView> findMeetings() {
		return meetingEventRepository.findAllByOrderByEventDateTimeDescIdDesc().stream()
			.map(this::toAdminMeetingView)
			.toList();
	}

	@Transactional
	public AdminMeetingView createMeeting(CreateMeetingCommand command, MultipartFile image, Long adminId) {
		validateCreateCommand(command, image);
		String imageUrl = storeImage(image);
		MeetingEvent event = meetingEventRepository.save(MeetingEvent.create(
			command.title().trim(),
			command.description().trim(),
			imageUrl,
			command.eventDateTime(),
			command.priceAmount(),
			command.capacity(),
			adminId
		));
		return toAdminMeetingView(event);
	}

	@Transactional(readOnly = true)
	public List<AdminMeetingApplicationView> findApplications(Long meetingId) {
		ensureMeetingExists(meetingId);
		return meetingApplicationRepository.findByMeetingEventIdOrderByCreatedAtDescIdDesc(meetingId).stream()
			.map(this::toApplicationView)
			.toList();
	}

	@Transactional
	public AdminMeetingApplicationView changeApplicationStatus(
		Long meetingId,
		Long applicationId,
		MeetingApplicationStatus status,
		String adminNote
	) {
		MeetingApplication application = findApplication(meetingId, applicationId);
		application.changeApplicationStatus(status, adminNote);
		return toApplicationView(application);
	}

	@Transactional
	public AdminMeetingApplicationView changePaymentStatus(
		Long meetingId,
		Long applicationId,
		MeetingPaymentStatus paymentStatus,
		String adminNote
	) {
		MeetingApplication application = findApplication(meetingId, applicationId);
		application.changePaymentStatus(paymentStatus, adminNote);
		return toApplicationView(application);
	}

	private void validateCreateCommand(CreateMeetingCommand command, MultipartFile image) {
		if (command == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting information is required.");
		}
		if (command.title() == null || command.title().trim().length() < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting title must be at least 2 characters.");
		}
		if (command.description() == null || command.description().trim().length() < 10) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting description must be at least 10 characters.");
		}
		if (command.eventDateTime() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting date is required.");
		}
		if (command.priceAmount() == null || command.priceAmount() < 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting price must be zero or more.");
		}
		if (command.capacity() == null || command.capacity() < 1) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting capacity must be at least 1.");
		}
		if (image == null || image.isEmpty()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting image is required.");
		}
	}

	private String storeImage(MultipartFile image) {
		if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting image must be 5MB or smaller.");
		}

		String contentType = normalizedContentType(image);
		if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Only jpeg, png, and webp images are allowed.");
		}

		String fileName = UUID.randomUUID() + "." + extension(contentType);
		Path uploadDir = Path.of(uploadProperties.rootDir(), "meeting-events")
			.toAbsolutePath()
			.normalize();
		Path uploadPath = uploadDir.resolve(fileName);

		try {
			Files.createDirectories(uploadDir);
			image.transferTo(uploadPath);
		} catch (IOException exception) {
			throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Meeting image could not be saved.");
		}

		return trimTrailingSlash(uploadProperties.publicPath()) + "/meeting-events/" + fileName;
	}

	private String normalizedContentType(MultipartFile image) {
		String contentType = image.getContentType();
		if (contentType != null && !contentType.isBlank()) {
			return contentType.toLowerCase(Locale.ROOT);
		}
		String fileName = image.getOriginalFilename();
		if (fileName == null) {
			return null;
		}
		String normalizedName = fileName.toLowerCase(Locale.ROOT);
		if (normalizedName.endsWith(".png")) {
			return "image/png";
		}
		if (normalizedName.endsWith(".webp")) {
			return "image/webp";
		}
		if (normalizedName.endsWith(".jpg") || normalizedName.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		return null;
	}

	private String extension(String contentType) {
		return switch (contentType) {
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			default -> "jpg";
		};
	}

	private void ensureMeetingExists(Long meetingId) {
		if (!meetingEventRepository.existsById(meetingId)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, "Meeting was not found.");
		}
	}

	private MeetingApplication findApplication(Long meetingId, Long applicationId) {
		MeetingApplication application = meetingApplicationRepository.findById(applicationId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Meeting application was not found."));
		if (!application.getMeetingEventId().equals(meetingId)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting application does not belong to this meeting.");
		}
		return application;
	}

	private AdminMeetingView toAdminMeetingView(MeetingEvent event) {
		long applicantCount = meetingApplicationRepository.countByMeetingEventId(event.getId());
		long confirmedCount = meetingApplicationRepository.countByMeetingEventIdAndApplicationStatusAndPaymentStatus(
			event.getId(),
			MeetingApplicationStatus.APPROVED,
			MeetingPaymentStatus.PAID
		);
		return new AdminMeetingView(
			event.getId(),
			event.getTitle(),
			event.getDescription(),
			event.getImageUrl(),
			event.getEventDateTime(),
			event.getPriceAmount(),
			event.getCapacity(),
			event.getStatus().name(),
			applicantCount,
			confirmedCount,
			event.getCreatedByAdminId(),
			event.getCreatedAt()
		);
	}

	private AdminMeetingApplicationView toApplicationView(MeetingApplication application) {
		UserAccount user = userAccountRepository.findById(application.getUserId()).orElse(null);
		UserProfile profile = userProfileRepository.findByUserId(application.getUserId()).orElse(null);
		ProfilePhoto photo = profilePhotoRepository.findFirstByUserIdOrderByDisplayOrderAscIdAsc(application.getUserId()).orElse(null);

		return new AdminMeetingApplicationView(
			application.getId(),
			application.getMeetingEventId(),
			application.getUserId(),
			user == null ? null : user.getName(),
			user == null ? null : age(user.getBirthDate()),
			user == null || user.getGender() == null ? null : user.getGender().name(),
			profile == null ? null : profile.getJob(),
			profile == null ? null : profile.getActivityRegion(),
			photo == null ? null : photo.getImageUrl(),
			application.getApplicationStatus().name(),
			application.getPaymentStatus().name(),
			application.getCreatedAt(),
			application.getConfirmedAt(),
			application.getAdminNote()
		);
	}

	private Integer age(LocalDate birthDate) {
		if (birthDate == null) {
			return null;
		}
		return Period.between(birthDate, LocalDate.now()).getYears();
	}

	private String trimTrailingSlash(String value) {
		if (value.endsWith("/")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}

	public record CreateMeetingCommand(
		String title,
		String description,
		LocalDateTime eventDateTime,
		Integer priceAmount,
		Integer capacity
	) {
	}

	public record AdminMeetingView(
		Long id,
		String title,
		String description,
		String imageUrl,
		LocalDateTime eventDateTime,
		Integer priceAmount,
		Integer capacity,
		String status,
		long applicantCount,
		long confirmedCount,
		Long createdByAdminId,
		Instant createdAt
	) {
	}

	public record AdminMeetingApplicationView(
		Long applicationId,
		Long meetingId,
		Long userId,
		String name,
		Integer age,
		String gender,
		String job,
		String activityRegion,
		String photoUrl,
		String applicationStatus,
		String paymentStatus,
		Instant appliedAt,
		Instant confirmedAt,
		String adminNote
	) {
	}
}
