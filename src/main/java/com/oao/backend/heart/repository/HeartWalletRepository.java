package com.oao.backend.heart.repository;

import com.oao.backend.heart.domain.HeartWallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HeartWalletRepository extends JpaRepository<HeartWallet, Long> {

	Optional<HeartWallet> findByUserId(Long userId);
}
