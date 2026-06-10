package com.oao.backend.matching.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "matching_profile")
public class MatchingProfile extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private String jobIntro;
	private String datingStyle;
	private boolean matchingEnabled;
	private Instant lastAutoMatchedAt;
	private int autoMatchCount;
	private int sGradeGuaranteedMatchCount;
	private int noResponseCount;

	protected MatchingProfile() {
	}

	public static MatchingProfile create(Long userId) {
		MatchingProfile profile = new MatchingProfile();
		profile.userId = userId;
		profile.matchingEnabled = false;
		profile.autoMatchCount = 0;
		profile.sGradeGuaranteedMatchCount = 0;
		profile.noResponseCount = 0;
		return profile;
	}

	public void updateIntro(String intro) {
		this.jobIntro = intro;
	}

	public boolean isEligibleForSGradeGuarantee(int guaranteeCount) {
		return sGradeGuaranteedMatchCount < guaranteeCount;
	}

	public void recordAutoMatch(boolean sGradeGuaranteed) {
		this.autoMatchCount++;
		this.lastAutoMatchedAt = Instant.now();
		if (sGradeGuaranteed) {
			this.sGradeGuaranteedMatchCount++;
		}
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getJobIntro() {
		return jobIntro;
	}

	public boolean isMatchingEnabled() {
		return matchingEnabled;
	}

	public Instant getLastAutoMatchedAt() {
		return lastAutoMatchedAt;
	}

	public int getAutoMatchCount() {
		return autoMatchCount;
	}
}
