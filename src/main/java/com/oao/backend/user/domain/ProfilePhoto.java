package com.oao.backend.user.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profile_photo")
public class ProfilePhoto extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private String imageUrl;
	private Integer displayOrder;

	@Enumerated(EnumType.STRING)
	private UserVerificationDocument.ReviewStatus reviewStatus = UserVerificationDocument.ReviewStatus.PENDING;

	protected ProfilePhoto() {
	}

	public static ProfilePhoto create(Long userId, String imageUrl, Integer displayOrder) {
		ProfilePhoto photo = new ProfilePhoto();
		photo.userId = userId;
		photo.imageUrl = imageUrl;
		photo.displayOrder = displayOrder;
		return photo;
	}

	public void updateImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
		this.reviewStatus = UserVerificationDocument.ReviewStatus.PENDING;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public Integer getDisplayOrder() {
		return displayOrder;
	}

	public UserVerificationDocument.ReviewStatus getReviewStatus() {
		return reviewStatus;
	}
}
