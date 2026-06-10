package com.oao.backend.user.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_profile")
public class UserProfile extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private Integer heightCm;
	private String bodyType;
	private String mbti;
	private String job;
	private String education;
	private String religion;
	private String activityRegion;
	private String smokingStatus;
	private String drinkingStatus;
	private String phoneNumber;
	private Instant phoneVerifiedAt;

	protected UserProfile() {
	}

	public static UserProfile create(
		Long userId,
		Integer heightCm,
		String mbti,
		String job,
		String education,
		String religion,
		String activityRegion,
		String smokingStatus,
		String drinkingStatus
	) {
		return create(
			userId,
			heightCm,
			null,
			mbti,
			job,
			education,
			religion,
			activityRegion,
			smokingStatus,
			drinkingStatus
		);
	}

	public static UserProfile create(
		Long userId,
		Integer heightCm,
		String bodyType,
		String mbti,
		String job,
		String education,
		String religion,
		String activityRegion,
		String smokingStatus,
		String drinkingStatus
	) {
		UserProfile profile = new UserProfile();
		profile.userId = userId;
		profile.updateOnboardingInfo(
			heightCm,
			bodyType,
			mbti,
			job,
			education,
			religion,
			activityRegion,
			smokingStatus,
			drinkingStatus
		);
		return profile;
	}

	public void updateOnboardingInfo(
		Integer heightCm,
		String mbti,
		String job,
		String education,
		String religion,
		String activityRegion,
		String smokingStatus,
		String drinkingStatus
	) {
		updateOnboardingInfo(
			heightCm,
			bodyType,
			mbti,
			job,
			education,
			religion,
			activityRegion,
			smokingStatus,
			drinkingStatus
		);
	}

	public void updateOnboardingInfo(
		Integer heightCm,
		String bodyType,
		String mbti,
		String job,
		String education,
		String religion,
		String activityRegion,
		String smokingStatus,
		String drinkingStatus
	) {
		this.heightCm = heightCm;
		this.bodyType = bodyType;
		this.mbti = mbti;
		this.job = job;
		this.education = education;
		this.religion = religion;
		this.activityRegion = activityRegion;
		this.smokingStatus = smokingStatus;
		this.drinkingStatus = drinkingStatus;
	}

	public void verifyPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
		this.phoneVerifiedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Integer getHeightCm() {
		return heightCm;
	}

	public String getBodyType() {
		return bodyType;
	}

	public String getMbti() {
		return mbti;
	}

	public String getJob() {
		return job;
	}

	public String getEducation() {
		return education;
	}

	public String getReligion() {
		return religion;
	}

	public String getActivityRegion() {
		return activityRegion;
	}

	public String getSmokingStatus() {
		return smokingStatus;
	}

	public String getDrinkingStatus() {
		return drinkingStatus;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public Instant getPhoneVerifiedAt() {
		return phoneVerifiedAt;
	}
}
