package com.oao.backend.notification.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.notification.domain.AppNotification;
import com.oao.backend.notification.domain.AppNotification.NotificationType;
import com.oao.backend.notification.repository.AppNotificationRepository;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppNotificationService {

	private static final int DEFAULT_LIMIT = 50;

	private final AppNotificationRepository notificationRepository;
	private final UserAccountRepository userAccountRepository;
	private final SimpMessagingTemplate messagingTemplate;

	public AppNotificationService(
		AppNotificationRepository notificationRepository,
		UserAccountRepository userAccountRepository,
		SimpMessagingTemplate messagingTemplate
	) {
		this.notificationRepository = notificationRepository;
		this.userAccountRepository = userAccountRepository;
		this.messagingTemplate = messagingTemplate;
	}

	@Transactional(readOnly = true)
	public List<AppNotificationView> notifications(Long userId) {
		return notificationRepository
			.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, DEFAULT_LIMIT))
			.stream()
			.map(AppNotificationService::toView)
			.toList();
	}

	@Transactional(readOnly = true)
	public UnreadCountView unreadCount(Long userId) {
		return new UnreadCountView(notificationRepository.countByUserIdAndReadAtIsNull(userId));
	}

	@Transactional
	public AppNotificationView markRead(Long notificationId, Long userId) {
		AppNotification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
			.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Notification not found."));
		notification.markRead();
		return toView(notification);
	}

	@Transactional
	public void markAllRead(Long userId) {
		notificationRepository
			.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, DEFAULT_LIMIT))
			.forEach(AppNotification::markRead);
	}

	@Transactional
	public void notifyMatchArrived(Long matchId, Long userAId, Long userBId) {
		createOnce(
			userAId,
			NotificationType.MATCH_ARRIVED,
			"새로운 인연이 도착했어요.",
			counterpartName(userBId) + "님과의 새로운 매칭을 확인해보세요.",
			"MATCH",
			"MATCH",
			matchId,
			"/matches/" + matchId,
			userBId
		);
		createOnce(
			userBId,
			NotificationType.MATCH_ARRIVED,
			"새로운 인연이 도착했어요.",
			counterpartName(userAId) + "님과의 새로운 매칭을 확인해보세요.",
			"MATCH",
			"MATCH",
			matchId,
			"/matches/" + matchId,
			userAId
		);
	}

	@Transactional
	public void notifyExpressReceived(Long interestId, Long receiverUserId, Long senderUserId) {
		createAndPush(
			receiverUserId,
			NotificationType.EXPRESS_RECEIVED,
			"공개 수락을 받았어요.",
			counterpartName(senderUserId) + "님이 공개 수락을 보냈어요.",
			"EXPRESS",
			"INTEREST",
			interestId,
			"/interests/profiles/" + senderUserId,
			senderUserId
		);
	}

	@Transactional
	public void notifyMatchCompleted(Long matchId, Long userAId, Long userBId) {
		createOnce(
			userAId,
			NotificationType.MATCH_COMPLETED,
			"매칭이 완료됐어요.",
			counterpartName(userBId) + "님과 대화방이 열렸어요.",
			"HEART",
			"MATCH",
			matchId,
			"/chat/matches/" + matchId,
			userBId
		);
		createOnce(
			userBId,
			NotificationType.MATCH_COMPLETED,
			"매칭이 완료됐어요.",
			counterpartName(userAId) + "님과 대화방이 열렸어요.",
			"HEART",
			"MATCH",
			matchId,
			"/chat/matches/" + matchId,
			userAId
		);
	}

	@Transactional
	public void notifyMessageReceived(Long roomId, Long messageId, Long receiverUserId, Long senderUserId) {
		createAndPush(
			receiverUserId,
			NotificationType.MESSAGE_RECEIVED,
			"새 메시지가 도착했어요.",
			counterpartName(senderUserId) + "님이 메시지를 보냈어요.",
			"MESSAGE",
			"CHAT_MESSAGE",
			messageId,
			"/chat/rooms/" + roomId,
			senderUserId
		);
	}

	private void createOnce(
		Long userId,
		NotificationType type,
		String title,
		String body,
		String iconType,
		String targetType,
		Long targetId,
		String linkUrl,
		Long actorUserId
	) {
		if (notificationRepository.existsByUserIdAndNotificationTypeAndTargetTypeAndTargetId(userId, type, targetType, targetId)) {
			return;
		}
		createAndPush(userId, type, title, body, iconType, targetType, targetId, linkUrl, actorUserId);
	}

	private void createAndPush(
		Long userId,
		NotificationType type,
		String title,
		String body,
		String iconType,
		String targetType,
		Long targetId,
		String linkUrl,
		Long actorUserId
	) {
		AppNotification notification = notificationRepository.save(AppNotification.create(
			userId,
			type,
			title,
			body,
			iconType,
			targetType,
			targetId,
			linkUrl,
			actorUserId
		));
		messagingTemplate.convertAndSend("/topic/users." + userId + ".notifications", toView(notification));
	}

	private String counterpartName(Long userId) {
		return userAccountRepository.findById(userId)
			.map(UserAccount::getName)
			.filter(name -> name != null && !name.isBlank())
			.orElse("상대방");
	}

	private static AppNotificationView toView(AppNotification notification) {
		return new AppNotificationView(
			notification.getId(),
			notification.getNotificationType().name(),
			notification.getTitle(),
			notification.getBody(),
			notification.getIconType(),
			notification.getTargetType(),
			notification.getTargetId(),
			notification.getLinkUrl(),
			notification.getActorUserId(),
			notification.getReadAt() == null,
			notification.getReadAt(),
			notification.getCreatedAt()
		);
	}

	public record AppNotificationView(
		Long id,
		String type,
		String title,
		String body,
		String iconType,
		String targetType,
		Long targetId,
		String linkUrl,
		Long actorUserId,
		boolean unread,
		Instant readAt,
		Instant createdAt
	) {
	}

	public record UnreadCountView(long count) {
	}
}
