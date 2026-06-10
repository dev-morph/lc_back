package com.oao.backend.dev.service;

import com.oao.backend.admin.service.AdminUserDataPurgeService;
import com.oao.backend.admin.service.AdminUserDataPurgeService.AdminUserPurgeResult;
import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.BusinessException;
import com.oao.backend.user.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DevAdminToolService {

	private static final String DEV_ADMIN_ROLE = "SUPER_ADMIN";
	private static final String ACTIVE_STATUS = "ACTIVE";

	private final JdbcTemplate jdbcTemplate;
	private final UserAccountRepository userAccountRepository;
	private final AdminUserDataPurgeService adminUserDataPurgeService;

	public DevAdminToolService(
		JdbcTemplate jdbcTemplate,
		UserAccountRepository userAccountRepository,
		AdminUserDataPurgeService adminUserDataPurgeService
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.userAccountRepository = userAccountRepository;
		this.adminUserDataPurgeService = adminUserDataPurgeService;
	}

	@Transactional
	public DevAdminPromotionResult promoteCurrentUser(KakaoPrincipal principal) {
		Long userId = principal.getUserId();
		if (!userAccountRepository.existsById(userId)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, "User not found.");
		}

		String email = normalizedEmail(principal);
		String name = normalizedName(principal);
		int updated = jdbcTemplate.update(
			"""
				update admin_user
				set email = ?, name = ?, role = ?, status = ?, updated_at = current_timestamp(6)
				where user_id = ?
				""",
			email,
			name,
			DEV_ADMIN_ROLE,
			ACTIVE_STATUS,
			userId
		);
		if (updated == 0) {
			jdbcTemplate.update(
				"""
					insert into admin_user (user_id, email, name, role, status, created_at, updated_at)
					values (?, ?, ?, ?, ?, current_timestamp(6), current_timestamp(6))
					""",
				userId,
				email,
				name,
				DEV_ADMIN_ROLE,
				ACTIVE_STATUS
			);
		}

		return new DevAdminPromotionResult(userId, email, name, DEV_ADMIN_ROLE, ACTIVE_STATUS);
	}

	@Transactional
	public AdminUserPurgeResult purgeCurrentUser(KakaoPrincipal principal) {
		return adminUserDataPurgeService.purgeDevSelfTestUserData(principal.getUserId());
	}

	private String normalizedEmail(KakaoPrincipal principal) {
		String email = principal.getEmail();
		if (email != null && !email.isBlank()) {
			return email.trim();
		}
		return "dev-user-" + principal.getUserId() + "@local.dev";
	}

	private String normalizedName(KakaoPrincipal principal) {
		String nickname = principal.getNickname();
		if (nickname != null && !nickname.isBlank()) {
			return nickname.trim();
		}
		String email = principal.getEmail();
		if (email != null && !email.isBlank()) {
			return email.trim();
		}
		return "Dev User " + principal.getUserId();
	}

	public record DevAdminPromotionResult(
		Long userId,
		String email,
		String name,
		String role,
		String status
	) {
	}
}
