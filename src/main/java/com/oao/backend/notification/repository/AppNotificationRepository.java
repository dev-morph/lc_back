package com.oao.backend.notification.repository;

import com.oao.backend.notification.domain.AppNotification;
import com.oao.backend.notification.domain.AppNotification.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {

	List<AppNotification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(Long userId);

	Optional<AppNotification> findByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndNotificationTypeAndTargetTypeAndTargetId(
		Long userId,
		NotificationType notificationType,
		String targetType,
		Long targetId
	);
}
