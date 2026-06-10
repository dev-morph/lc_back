package com.oao.backend.heart.repository;

import com.oao.backend.heart.domain.HeartTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeartTransactionRepository extends JpaRepository<HeartTransaction, Long> {

	List<HeartTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
