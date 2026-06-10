package com.oao.backend.user.repository;

import com.oao.backend.user.domain.AdminUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

	Optional<AdminUser> findByUserIdAndStatus(Long userId, String status);

	boolean existsByUserId(Long userId);
}
