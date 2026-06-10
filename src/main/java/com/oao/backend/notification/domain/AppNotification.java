package com.oao.backend.notification.domain;

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
@Table(name = "app_notification")
public class AppNotification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;

	@Enumerated(EnumType.STRING)
	private NotificationType notificationType;

	private String title;

	@Column(length = 500)
	private String body;

	private String iconType;
	private String targetType;
	private Long targetId;
	private String linkUrl;
	private Long actorUserId;
	private Instant readAt;

	protected AppNotification() {
	}

	public static AppNotification create(
		Long userId,
		NotificationType notificationType,
		String title,
		String body,
		String iconType,
		String targetType,
		Long targetId,
		String linkUrl,
		Long actorUserId
	) {
		AppNotification notification = new AppNotification();
		notification.userId = userId;
		notification.notificationType = notificationType;
		notification.title = title;
		notification.body = body;
		notification.iconType = iconType;
		notification.targetType = targetType;
		notification.targetId = targetId;
		notification.linkUrl = linkUrl;
		notification.actorUserId = actorUserId;
		return notification;
	}

	public void markRead() {
		if (readAt == null) {
			readAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public NotificationType getNotificationType() {
		return notificationType;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public String getIconType() {
		return iconType;
	}

	public String getTargetType() {
		return targetType;
	}

	public Long getTargetId() {
		return targetId;
	}

	public String getLinkUrl() {
		return linkUrl;
	}

	public Long getActorUserId() {
		return actorUserId;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public enum NotificationType {
		MATCH_ARRIVED,
		EXPRESS_RECEIVED,
		MATCH_COMPLETED,
		MESSAGE_RECEIVED
	}
}
