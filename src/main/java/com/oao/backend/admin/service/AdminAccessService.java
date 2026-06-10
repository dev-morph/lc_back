package com.oao.backend.admin.service;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.AdminUser;
import com.oao.backend.user.repository.AdminUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccessService {

	private static final String ACTIVE_STATUS = "ACTIVE";

	private final AdminUserRepository adminUserRepository;

	public AdminAccessService(AdminUserRepository adminUserRepository) {
		this.adminUserRepository = adminUserRepository;
	}

	@Transactional(readOnly = true)
	public AdminUser requireActiveAdmin(KakaoPrincipal principal) {
		if (principal == null || principal.getUserId() == null) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "Admin access is required.");
		}
		return adminUserRepository.findByUserIdAndStatus(principal.getUserId(), ACTIVE_STATUS)
			.orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "Admin access is required."));
	}

	@Transactional(readOnly = true)
	public AdminUser findActiveAdminOrNull(KakaoPrincipal principal) {
		if (principal == null || principal.getUserId() == null) {
			return null;
		}
		return adminUserRepository.findByUserIdAndStatus(principal.getUserId(), ACTIVE_STATUS).orElse(null);
	}
}
