package com.oao.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oao.upload")
public record UploadProperties(String rootDir, String publicPath) {

	public UploadProperties {
		if (rootDir == null || rootDir.isBlank()) {
			rootDir = "uploads";
		}
		if (publicPath == null || publicPath.isBlank()) {
			publicPath = "/uploads";
		}
	}
}
