package com.oao.backend.heart.domain;

import com.oao.backend.common.BaseTimeEntity;
import com.oao.backend.common.BusinessException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "instant_introduction_config")
public class InstantIntroductionConfig extends BaseTimeEntity {

	public static final Long DEFAULT_ID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private int firstUsageCost;
	private int midTierStartCount;
	private int midTierEndCount;
	private int midTierCost;
	private int highTierStartCount;
	private int highTierCost;
	private int usageWindowHours;
	private Long updatedByAdminId;

	protected InstantIntroductionConfig() {
	}

	public static InstantIntroductionConfig defaultConfig() {
		InstantIntroductionConfig config = new InstantIntroductionConfig();
		config.id = DEFAULT_ID;
		config.firstUsageCost = 1;
		config.midTierStartCount = 2;
		config.midTierEndCount = 5;
		config.midTierCost = 3;
		config.highTierStartCount = 6;
		config.highTierCost = 5;
		config.usageWindowHours = 24;
		return config;
	}

	public void update(
		int firstUsageCost,
		int midTierEndCount,
		int midTierCost,
		int highTierCost,
		int usageWindowHours,
		Long updatedByAdminId
	) {
		if (firstUsageCost <= 0 || midTierCost <= 0 || highTierCost <= 0) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Heart costs must be positive.");
		}
		if (midTierEndCount < 2) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Mid tier end count must be at least 2.");
		}
		if (usageWindowHours <= 0 || usageWindowHours > 720) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Usage window hours must be between 1 and 720.");
		}

		this.firstUsageCost = firstUsageCost;
		this.midTierStartCount = 2;
		this.midTierEndCount = midTierEndCount;
		this.midTierCost = midTierCost;
		this.highTierStartCount = midTierEndCount + 1;
		this.highTierCost = highTierCost;
		this.usageWindowHours = usageWindowHours;
		this.updatedByAdminId = updatedByAdminId;
	}

	public int costFor(int usageCountInWindow) {
		if (usageCountInWindow <= 1) {
			return firstUsageCost;
		}
		if (usageCountInWindow <= midTierEndCount) {
			return midTierCost;
		}
		return highTierCost;
	}

	public Long getId() {
		return id;
	}

	public int getFirstUsageCost() {
		return firstUsageCost;
	}

	public int getMidTierStartCount() {
		return midTierStartCount;
	}

	public int getMidTierEndCount() {
		return midTierEndCount;
	}

	public int getMidTierCost() {
		return midTierCost;
	}

	public int getHighTierStartCount() {
		return highTierStartCount;
	}

	public int getHighTierCost() {
		return highTierCost;
	}

	public int getUsageWindowHours() {
		return usageWindowHours;
	}

	public Long getUpdatedByAdminId() {
		return updatedByAdminId;
	}
}
