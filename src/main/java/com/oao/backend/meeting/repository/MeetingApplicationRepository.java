package com.oao.backend.meeting.repository;

import com.oao.backend.meeting.domain.MeetingApplication;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingApplicationRepository extends JpaRepository<MeetingApplication, Long> {

	List<MeetingApplication> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	List<MeetingApplication> findByMeetingEventIdOrderByCreatedAtDescIdDesc(Long meetingEventId);

	Optional<MeetingApplication> findByMeetingEventIdAndUserId(Long meetingEventId, Long userId);

	long countByMeetingEventId(Long meetingEventId);

	long countByMeetingEventIdAndApplicationStatusAndPaymentStatus(
		Long meetingEventId,
		MeetingApplication.MeetingApplicationStatus applicationStatus,
		MeetingApplication.MeetingPaymentStatus paymentStatus
	);
}
