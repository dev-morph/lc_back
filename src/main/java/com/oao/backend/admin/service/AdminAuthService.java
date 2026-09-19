package com.oao.backend.admin.service;

import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.AdminUser;
import com.oao.backend.user.repository.AdminUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

	static final String ADMIN_SESSION_ATTRIBUTE = "OAO_ADMIN_USER_ID";
	private static final String ACTIVE_STATUS = "ACTIVE";

	private final AdminUserRepository adminUserRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminAuthService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder) {
		this.adminUserRepository = adminUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AdminUser login(String email, String password, HttpServletRequest request) {
		String normalizedEmail = normalizeEmail(email);
		AdminUser admin = adminUserRepository.findByEmailIgnoreCase(normalizedEmail)
			.filter(AdminUser::isActive)
			.filter(candidate -> hasUsablePassword(candidate, password))
			.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials."));

		HttpSession session = request.getSession(true);
		request.changeSessionId();
		session.setAttribute(ADMIN_SESSION_ATTRIBUTE, admin.getId());
		admin.recordLogin();
		return admin;
	}

	public void logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.removeAttribute(ADMIN_SESSION_ATTRIBUTE);
		}
	}

	@Transactional(readOnly = true)
	public AdminUser findCurrentAdminOrNull(HttpServletRequest request) {
		Long adminId = currentAdminId(request);
		if (adminId == null) {
			return null;
		}
		return adminUserRepository.findByIdAndStatus(adminId, ACTIVE_STATUS).orElse(null);
	}

	private boolean hasUsablePassword(AdminUser admin, String rawPassword) {
		String passwordHash = admin.getPasswordHash();
		return passwordHash != null && rawPassword != null && passwordEncoder.matches(rawPassword, passwordHash);
	}

	private Long currentAdminId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return null;
		}
		Object value = session.getAttribute(ADMIN_SESSION_ATTRIBUTE);
		if (value instanceof Long adminId) {
			return adminId;
		}
		if (value instanceof Number number) {
			return number.longValue();
		}
		return null;
	}

	public static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
