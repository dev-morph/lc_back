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
import java.time.Instant;

@Entity
@Table(name = "meeting_application")
public class MeetingApplication extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long meetingEventId;

	@Column(nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MeetingApplicationStatus applicationStatus = MeetingApplicationStatus.APPLIED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MeetingPaymentStatus paymentStatus = MeetingPaymentStatus.PENDING;

	private Instant confirmedAt;

	@Column(length = 512)
	private String adminNote;

	protected MeetingApplication() {
	}

	public static MeetingApplication apply(Long meetingEventId, Long userId) {
		MeetingApplication application = new MeetingApplication();
		application.meetingEventId = meetingEventId;
		application.userId = userId;
		application.applicationStatus = MeetingApplicationStatus.APPLIED;
		application.paymentStatus = MeetingPaymentStatus.PENDING;
		return application;
	}

	public void changeApplicationStatus(MeetingApplicationStatus applicationStatus, String adminNote) {
		this.applicationStatus = applicationStatus;
		this.adminNote = normalizeNote(adminNote);
		refreshConfirmedAt();
	}

	public void changePaymentStatus(MeetingPaymentStatus paymentStatus, String adminNote) {
		this.paymentStatus = paymentStatus;
		this.adminNote = normalizeNote(adminNote);
		refreshConfirmedAt();
	}

	private void refreshConfirmedAt() {
		if (this.applicationStatus == MeetingApplicationStatus.APPROVED && this.paymentStatus == MeetingPaymentStatus.PAID) {
			if (this.confirmedAt == null) {
				this.confirmedAt = Instant.now();
			}
			return;
		}
		this.confirmedAt = null;
	}

	private String normalizeNote(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	public Long getId() {
		return id;
	}

	public Long getMeetingEventId() {
		return meetingEventId;
	}

	public Long getUserId() {
		return userId;
	}

	public MeetingApplicationStatus getApplicationStatus() {
		return applicationStatus;
	}

	public MeetingPaymentStatus getPaymentStatus() {
		return paymentStatus;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public String getAdminNote() {
		return adminNote;
	}

	public enum MeetingApplicationStatus {
		APPLIED,
		APPROVED,
		REJECTED,
		CANCELLED
	}

	public enum MeetingPaymentStatus {
		PENDING,
		PAID,
		REFUNDED
	}
}
