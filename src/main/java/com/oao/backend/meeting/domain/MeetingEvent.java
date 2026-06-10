package com.oao.backend.meeting.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "meeting_event")
public class MeetingEvent extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, columnDefinition = "longtext")
	private String description;

	@Column(nullable = false, length = 512)
	private String imageUrl;

	@Column(nullable = false)
	private LocalDateTime eventDateTime;

	@Column(nullable = false)
	private Integer priceAmount;

	@Column(nullable = false)
	private Integer capacity;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MeetingEventStatus status = MeetingEventStatus.OPEN;

	private Long createdByAdminId;

	protected MeetingEvent() {
	}

	public static MeetingEvent create(
		String title,
		String description,
		String imageUrl,
		LocalDateTime eventDateTime,
		Integer priceAmount,
		Integer capacity,
		Long createdByAdminId
	) {
		MeetingEvent event = new MeetingEvent();
		event.title = title;
		event.description = description;
		event.imageUrl = imageUrl;
		event.eventDateTime = eventDateTime;
		event.priceAmount = priceAmount;
		event.capacity = capacity;
		event.createdByAdminId = createdByAdminId;
		event.status = MeetingEventStatus.OPEN;
		return event;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public LocalDateTime getEventDateTime() {
		return eventDateTime;
	}

	public Integer getPriceAmount() {
		return priceAmount;
	}

	public Integer getCapacity() {
		return capacity;
	}

	public MeetingEventStatus getStatus() {
		return status;
	}

	public Long getCreatedByAdminId() {
		return createdByAdminId;
	}

	public enum MeetingEventStatus {
		DRAFT,
		OPEN,
		CLOSED,
		CANCELLED
	}
}
