package com.oao.backend.config;

import com.oao.backend.auth.KakaoOAuth2UserService;
import com.oao.backend.auth.OAuth2LoginSuccessHandler;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	private final String allowedOrigins;

	public SecurityConfig(
		@Value("${oao.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String allowedOrigins
	) {
		this.allowedOrigins = allowedOrigins;
	}

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		KakaoOAuth2UserService kakaoOAuth2UserService,
		OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler
	) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource()))
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/health").permitAll()
				.requestMatchers("/oauth2/**", "/login/**").permitAll()
				.anyRequest().permitAll()
			)
			.oauth2Login(oauth2 -> oauth2
				.userInfoEndpoint(userInfo -> userInfo.userService(kakaoOAuth2UserService))
				.successHandler(oAuth2LoginSuccessHandler)
			)
			.logout(logout -> logout
				.logoutUrl("/auth/logout")
				.logoutSuccessUrl("/api/health")
			);

		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(allowedOriginPatterns());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	private List<String> allowedOriginPatterns() {
		List<String> configuredOrigins = Arrays.stream(allowedOrigins.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isBlank())
			.toList();

		if (configuredOrigins.isEmpty()) {
			return List.of("http://localhost:3000", "http://127.0.0.1:3000");
		}

		return configuredOrigins;
	}
}
