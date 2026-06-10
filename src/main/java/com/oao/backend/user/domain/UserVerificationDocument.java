package com.oao.backend.user.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_verification_document")
public class UserVerificationDocument extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private String documentType;
	private String fileUrl;

	@Enumerated(EnumType.STRING)
	private ReviewStatus reviewStatus = ReviewStatus.PENDING;

	private String rejectionReason;
	private Long reviewedByAdminId;
	private Instant reviewedAt;

	protected UserVerificationDocument() {
	}

	public static UserVerificationDocument create(Long userId, String documentType, String fileUrl) {
		UserVerificationDocument document = new UserVerificationDocument();
		document.userId = userId;
		document.documentType = documentType;
		document.fileUrl = fileUrl;
		return document;
	}

	public void approve(Long adminId) {
		this.reviewStatus = ReviewStatus.APPROVED;
		this.reviewedByAdminId = adminId;
		this.reviewedAt = Instant.now();
	}

	public void reject(Long adminId, String reason) {
		this.reviewStatus = ReviewStatus.REJECTED;
		this.reviewedByAdminId = adminId;
		this.reviewedAt = Instant.now();
		this.rejectionReason = reason;
	}

	public Long getId() {
		return id;
	}

	public ReviewStatus getReviewStatus() {
		return reviewStatus;
	}

	public enum ReviewStatus {
		PENDING,
		APPROVED,
		REJECTED
	}
}
