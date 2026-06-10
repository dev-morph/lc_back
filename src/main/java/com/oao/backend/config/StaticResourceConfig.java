package com.oao.backend.config;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

	private final UploadProperties uploadProperties;

	public StaticResourceConfig(UploadProperties uploadProperties) {
		this.uploadProperties = uploadProperties;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String publicPathPattern = trimTrailingSlash(uploadProperties.publicPath()) + "/**";
		String uploadLocation = Path.of(uploadProperties.rootDir()).toAbsolutePath().normalize().toUri().toString();
		registry.addResourceHandler(publicPathPattern)
			.addResourceLocations(uploadLocation.endsWith("/") ? uploadLocation : uploadLocation + "/");
	}

	private String trimTrailingSlash(String value) {
		if (value.endsWith("/")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}
}
