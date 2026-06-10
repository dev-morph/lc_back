package com.oao.backend.heart.domain;

import com.oao.backend.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "instant_intro_usage_window")
public class InstantIntroUsageWindow extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private Instant windowStartedAt;
	private Instant windowExpiresAt;
	private int usageCount;

	protected InstantIntroUsageWindow() {
	}

	public static InstantIntroUsageWindow start(Long userId, Instant now, int windowHours) {
		InstantIntroUsageWindow window = new InstantIntroUsageWindow();
		window.userId = userId;
		window.windowStartedAt = now;
		window.windowExpiresAt = now.plusSeconds(windowHours * 60L * 60L);
		window.usageCount = 0;
		return window;
	}

	public boolean isExpired(Instant now) {
		return !windowExpiresAt.isAfter(now);
	}

	public void recordUsage() {
		this.usageCount++;
	}

	public int nextUsageCount() {
		return usageCount + 1;
	}

	public Instant getWindowExpiresAt() {
		return windowExpiresAt;
	}

	public int getUsageCount() {
		return usageCount;
	}
}
