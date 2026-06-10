package com.oao.backend.premium.repository;

import com.oao.backend.premium.domain.PremiumIntroRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PremiumIntroRequestRepository extends JpaRepository<PremiumIntroRequest, Long> {

	List<PremiumIntroRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
