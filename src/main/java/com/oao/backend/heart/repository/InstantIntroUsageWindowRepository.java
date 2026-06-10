package com.oao.backend.heart.repository;

import com.oao.backend.heart.domain.InstantIntroUsageWindow;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstantIntroUsageWindowRepository extends JpaRepository<InstantIntroUsageWindow, Long> {

	Optional<InstantIntroUsageWindow> findTopByUserIdOrderByWindowStartedAtDesc(Long userId);
}
