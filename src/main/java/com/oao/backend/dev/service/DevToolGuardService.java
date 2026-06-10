package com.oao.backend.dev.service;

import com.oao.backend.auth.KakaoPrincipal;
import com.oao.backend.common.BusinessException;
import com.oao.backend.config.DevToolsProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DevToolGuardService {

	private final DevToolsProperties devToolsProperties;

	public DevToolGuardService(DevToolsProperties devToolsProperties) {
		this.devToolsProperties = devToolsProperties;
	}

	public KakaoPrincipal requireAccess(KakaoPrincipal principal, String secret) {
		requireSecret(secret);
		if (principal == null || principal.getUserId() == null) {
			throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
		}
		return principal;
	}

	public void requireSecret(String secret) {
		if (!devToolsProperties.available() || !matchesSecret(secret)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "Dev tools access is denied.");
		}
	}

	private boolean matchesSecret(String secret) {
		if (secret == null) {
			return false;
		}
		byte[] expected = devToolsProperties.secret().getBytes(StandardCharsets.UTF_8);
		byte[] actual = secret.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expected, actual);
	}
}
