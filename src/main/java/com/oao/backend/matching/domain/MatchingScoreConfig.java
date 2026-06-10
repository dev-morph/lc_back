package com.oao.backend.matching.domain;

import com.oao.backend.common.BaseTimeEntity;
import com.oao.backend.common.BusinessException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "matching_score_config")
public class MatchingScoreConfig extends BaseTimeEntity {

	public static final Long DEFAULT_ID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private int hobbyPointPerMatch;
	private int hobbyMaxPoint;
	private int sameSmokingPoint;
	private int sameDrinkingPoint;
	private int sameReligionPoint;
	private int sameGradePoint;
	private int adjacentGradePoint;
	private boolean allowPreviousAutoMatch;
	private Long updatedByAdminId;

	protected MatchingScoreConfig() {
	}

	public static MatchingScoreConfig defaultConfig() {
		MatchingScoreConfig config = new MatchingScoreConfig();
		config.id = DEFAULT_ID;
		config.hobbyPointPerMatch = 5;
		config.hobbyMaxPoint = 25;
		config.sameSmokingPoint = 10;
		config.sameDrinkingPoint = 5;
		config.sameReligionPoint = 10;
		config.sameGradePoint = 10;
		config.adjacentGradePoint = 5;
		config.allowPreviousAutoMatch = false;
		return config;
	}

	public void update(
		int hobbyPointPerMatch,
		int hobbyMaxPoint,
		int sameSmokingPoint,
		int sameDrinkingPoint,
		int sameReligionPoint,
		int sameGradePoint,
		int adjacentGradePoint,
		boolean allowPreviousAutoMatch,
		Long updatedByAdminId
	) {
		if (hobbyPointPerMatch < 0
			|| hobbyMaxPoint < 0
			|| sameSmokingPoint < 0
			|| sameDrinkingPoint < 0
			|| sameReligionPoint < 0
			|| sameGradePoint < 0
			|| adjacentGradePoint < 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Matching score points must be zero or positive.");
		}
		this.hobbyPointPerMatch = hobbyPointPerMatch;
		this.hobbyMaxPoint = hobbyMaxPoint;
		this.sameSmokingPoint = sameSmokingPoint;
		this.sameDrinkingPoint = sameDrinkingPoint;
		this.sameReligionPoint = sameReligionPoint;
		this.sameGradePoint = sameGradePoint;
		this.adjacentGradePoint = adjacentGradePoint;
		this.allowPreviousAutoMatch = allowPreviousAutoMatch;
		this.updatedByAdminId = updatedByAdminId;
	}

	public Long getId() {
		return id;
	}

	public int getHobbyPointPerMatch() {
		return hobbyPointPerMatch;
	}

	public int getHobbyMaxPoint() {
		return hobbyMaxPoint;
	}

	public int getSameSmokingPoint() {
		return sameSmokingPoint;
	}

	public int getSameDrinkingPoint() {
		return sameDrinkingPoint;
	}

	public int getSameReligionPoint() {
		return sameReligionPoint;
	}

	public int getSameGradePoint() {
		return sameGradePoint;
	}

	public int getAdjacentGradePoint() {
		return adjacentGradePoint;
	}

	public boolean isAllowPreviousAutoMatch() {
		return allowPreviousAutoMatch;
	}

	public Long getUpdatedByAdminId() {
		return updatedByAdminId;
	}
}
