package com.oao.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oao.dev-tools")
public record DevToolsProperties(boolean enabled, String secret) {

	public boolean available() {
		return enabled && secret != null && !secret.isBlank();
	}
}
