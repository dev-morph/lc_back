package com.oao.backend.meeting.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.meeting.domain.MeetingApplication;
import com.oao.backend.meeting.domain.MeetingApplication.MeetingApplicationStatus;
import com.oao.backend.meeting.domain.MeetingApplication.MeetingPaymentStatus;
import com.oao.backend.meeting.domain.MeetingEvent;
import com.oao.backend.meeting.domain.MeetingEvent.MeetingEventStatus;
import com.oao.backend.meeting.repository.MeetingApplicationRepository;
import com.oao.backend.meeting.repository.MeetingEventRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {

	private final MeetingEventRepository meetingEventRepository;
	private final MeetingApplicationRepository meetingApplicationRepository;

	public MeetingService(
		MeetingEventRepository meetingEventRepository,
		MeetingApplicationRepository meetingApplicationRepository
	) {
		this.meetingEventRepository = meetingEventRepository;
		this.meetingApplicationRepository = meetingApplicationRepository;
	}

	@Transactional(readOnly = true)
	public List<MeetingView> findMeetings(Long userId, String tabValue) {
		MeetingTab tab = MeetingTab.from(tabValue);
		List<MeetingApplication> applications = meetingApplicationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId);
		Map<Long, MeetingApplication> applicationByEventId = applications.stream()
			.collect(Collectors.toMap(MeetingApplication::getMeetingEventId, Function.identity(), (left, right) -> left));

		if (tab == MeetingTab.ONGOING) {
			return meetingEventRepository.findByStatusOrderByEventDateTimeAscIdAsc(MeetingEventStatus.OPEN).stream()
				.filter(event -> !applicationByEventId.containsKey(event.getId()))
				.map(event -> toView(event, null))
				.toList();
		}

		List<MeetingApplication> filteredApplications = applications.stream()
			.filter(application -> tab.matches(application))
			.toList();
		Map<Long, MeetingEvent> eventById = meetingEventRepository.findAllById(
			filteredApplications.stream().map(MeetingApplication::getMeetingEventId).toList()
		).stream().collect(Collectors.toMap(MeetingEvent::getId, Function.identity()));

		return filteredApplications.stream()
			.map(application -> toView(eventById.get(application.getMeetingEventId()), application))
			.filter(view -> view != null)
			.sorted(Comparator.comparing(MeetingView::eventDateTime))
			.toList();
	}

	@Transactional(readOnly = true)
	public MeetingView findMeeting(Long meetingId, Long userId) {
		MeetingEvent event = findEvent(meetingId);
		MeetingApplication application = meetingApplicationRepository
			.findByMeetingEventIdAndUserId(meetingId, userId)
			.orElse(null);
		return toView(event, application);
	}

	@Transactional
	public MeetingView apply(Long meetingId, Long userId) {
		MeetingEvent event = findEvent(meetingId);
		if (event.getStatus() != MeetingEventStatus.OPEN) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "This meeting is not open for applications.");
		}
		if (meetingApplicationRepository.findByMeetingEventIdAndUserId(meetingId, userId).isPresent()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "You have already applied to this meeting.");
		}

		MeetingApplication application = meetingApplicationRepository.save(MeetingApplication.apply(meetingId, userId));
		return toView(event, application);
	}

	private MeetingEvent findEvent(Long meetingId) {
		return meetingEventRepository.findById(meetingId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Meeting was not found."));
	}

	private MeetingView toView(MeetingEvent event, MeetingApplication application) {
		if (event == null) {
			return null;
		}
		long applicantCount = meetingApplicationRepository.countByMeetingEventId(event.getId());
		long confirmedCount = meetingApplicationRepository.countByMeetingEventIdAndApplicationStatusAndPaymentStatus(
			event.getId(),
			MeetingApplicationStatus.APPROVED,
			MeetingPaymentStatus.PAID
		);
		return new MeetingView(
			event.getId(),
			event.getTitle(),
			event.getDescription(),
			event.getImageUrl(),
			event.getEventDateTime(),
			event.getPriceAmount(),
			event.getCapacity(),
			event.getStatus().name(),
			application == null ? null : application.getApplicationStatus().name(),
			application == null ? null : application.getPaymentStatus().name(),
			participationStatus(application).name(),
			applicantCount,
			confirmedCount
		);
	}

	private ParticipationStatus participationStatus(MeetingApplication application) {
		if (application == null) {
			return ParticipationStatus.AVAILABLE;
		}
		if (application.getApplicationStatus() == MeetingApplicationStatus.REJECTED) {
			return ParticipationStatus.REJECTED;
		}
		if (application.getApplicationStatus() == MeetingApplicationStatus.CANCELLED) {
			return ParticipationStatus.CANCELLED;
		}
		if (
			application.getApplicationStatus() == MeetingApplicationStatus.APPROVED
				&& application.getPaymentStatus() == MeetingPaymentStatus.PAID
		) {
			return ParticipationStatus.PARTICIPATING;
		}
		return ParticipationStatus.APPLIED;
	}

	enum MeetingTab {
		ONGOING,
		APPLIED,
		PARTICIPATING;

		static MeetingTab from(String value) {
			if (value == null || value.isBlank()) {
				return ONGOING;
			}
			return switch (value.trim().toUpperCase()) {
				case "ONGOING" -> ONGOING;
				case "APPLIED" -> APPLIED;
				case "PARTICIPATING" -> PARTICIPATING;
				default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "Meeting tab is invalid.");
			};
		}

		boolean matches(MeetingApplication application) {
			if (this == PARTICIPATING) {
				return application.getApplicationStatus() == MeetingApplicationStatus.APPROVED
					&& application.getPaymentStatus() == MeetingPaymentStatus.PAID;
			}
			if (this == APPLIED) {
				return application.getApplicationStatus() != MeetingApplicationStatus.REJECTED
					&& application.getApplicationStatus() != MeetingApplicationStatus.CANCELLED
					&& !(
						application.getApplicationStatus() == MeetingApplicationStatus.APPROVED
							&& application.getPaymentStatus() == MeetingPaymentStatus.PAID
					);
			}
			return false;
		}
	}

	enum ParticipationStatus {
		AVAILABLE,
		APPLIED,
		PARTICIPATING,
		REJECTED,
		CANCELLED
	}

	public record MeetingView(
		Long id,
		String title,
		String description,
		String imageUrl,
		LocalDateTime eventDateTime,
		Integer priceAmount,
		Integer capacity,
		String status,
		String applicationStatus,
		String paymentStatus,
		String participationStatus,
		long applicantCount,
		long confirmedCount
	) {
	}
}
