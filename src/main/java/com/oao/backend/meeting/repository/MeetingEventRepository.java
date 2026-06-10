package com.oao.backend.meeting.repository;

import com.oao.backend.meeting.domain.MeetingEvent;
import com.oao.backend.meeting.domain.MeetingEvent.MeetingEventStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingEventRepository extends JpaRepository<MeetingEvent, Long> {

	List<MeetingEvent> findByStatusOrderByEventDateTimeAscIdAsc(MeetingEventStatus status);

	List<MeetingEvent> findAllByOrderByEventDateTimeDescIdDesc();
}
