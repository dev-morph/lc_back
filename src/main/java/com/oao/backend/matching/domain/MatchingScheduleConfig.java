package com.oao.backend.matching.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "matching_schedule_config")
public class MatchingScheduleConfig extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private boolean enabled;
	private String cronExpression;
	private String runTimes;
	private String timezone;
	private Long updatedByAdminId;

	protected MatchingScheduleConfig() {
	}

	public void update(boolean enabled, String cronExpression, String runTimes, String timezone, Long adminId) {
		this.enabled = enabled;
		this.cronExpression = cronExpression;
		this.runTimes = runTimes;
		this.timezone = timezone;
		this.updatedByAdminId = adminId;
	}

	public Long getId() {
		return id;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public String getCronExpression() {
		return cronExpression;
	}

	public String getRunTimes() {
		return runTimes;
	}

	public String getTimezone() {
		return timezone;
	}

	public Long getUpdatedByAdminId() {
		return updatedByAdminId;
	}
}
