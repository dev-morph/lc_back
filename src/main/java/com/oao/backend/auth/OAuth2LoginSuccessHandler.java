package com.oao.backend.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final String successRedirectUrl;

	public OAuth2LoginSuccessHandler(
		@Value("${oao.auth.oauth-success-redirect-url}") String successRedirectUrl
	) {
		this.successRedirectUrl = successRedirectUrl;
	}

	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException, ServletException {
		Object principal = authentication.getPrincipal();
		if (!(principal instanceof KakaoPrincipal kakaoPrincipal)) {
			response.sendRedirect(successRedirectUrl);
			return;
		}

		String redirectUrl = UriComponentsBuilder.fromUriString(successRedirectUrl)
			.queryParam("userId", kakaoPrincipal.getUserId())
			.queryParam("approvalStatus", kakaoPrincipal.getApprovalStatus().name())
			.build()
			.toUriString();
		response.sendRedirect(redirectUrl);
	}
}
