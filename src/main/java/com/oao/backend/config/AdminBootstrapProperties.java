package com.oao.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oao.admin.bootstrap")
public record AdminBootstrapProperties(String email, String password, String name) {
}
