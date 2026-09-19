package com.oao.backend.admin.service;

import com.oao.backend.config.AdminBootstrapProperties;
import com.oao.backend.user.domain.AdminUser;
import com.oao.backend.user.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
	private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

	private final AdminBootstrapProperties properties;
	private final AdminUserRepository adminUserRepository;
	private final PasswordEncoder passwordEncoder;

	public AdminBootstrapRunner(
		AdminBootstrapProperties properties,
		AdminUserRepository adminUserRepository,
		PasswordEncoder passwordEncoder
	) {
		this.properties = properties;
		this.adminUserRepository = adminUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		String email = AdminAuthService.normalizeEmail(properties.email());
		String password = properties.password() == null ? "" : properties.password().trim();
		if (email.isBlank() && password.isBlank()) {
			return;
		}
		if (email.isBlank() || password.isBlank()) {
			log.warn("Admin bootstrap skipped because email or password is missing.");
			return;
		}

		String name = properties.name() == null || properties.name().isBlank()
			? "관리자"
			: properties.name().trim();
		String passwordHash = passwordEncoder.encode(password);

		AdminUser admin = adminUserRepository.findByEmailIgnoreCase(email)
			.orElseGet(() -> AdminUser.createEmailAdmin(email, name, SUPER_ADMIN_ROLE, passwordHash));
		admin.updateEmailLogin(email, name, SUPER_ADMIN_ROLE, passwordHash);
		adminUserRepository.save(admin);
		log.info("Admin bootstrap completed for {}", email);
	}
}
