package com.oao.backend.auth;

import com.oao.backend.common.BusinessException;
import com.oao.backend.user.domain.OAuthAccount;
import com.oao.backend.user.domain.OAuthAccount.OAuthProvider;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.OAuthAccountRepository;
import com.oao.backend.user.repository.UserAccountRepository;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KakaoOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
	private final OAuthAccountRepository oAuthAccountRepository;
	private final UserAccountRepository userAccountRepository;

	public KakaoOAuth2UserService(
		OAuthAccountRepository oAuthAccountRepository,
		UserAccountRepository userAccountRepository
	) {
		this.oAuthAccountRepository = oAuthAccountRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	@Transactional
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oAuth2User = delegate.loadUser(userRequest);
		String registrationId = userRequest.getClientRegistration().getRegistrationId();
		if (!"kakao".equals(registrationId)) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "Only Kakao OAuth is supported.");
		}

		KakaoUserInfo kakaoUserInfo = KakaoUserInfo.from(oAuth2User.getAttributes());
		OAuthAccount oAuthAccount = oAuthAccountRepository
			.findByProviderAndProviderUserId(OAuthProvider.KAKAO, kakaoUserInfo.providerUserId())
			.orElseGet(() -> createOAuthAccount(kakaoUserInfo));

		UserAccount user = userAccountRepository.findById(oAuthAccount.getUserId())
			.orElseGet(() -> reconnectOAuthAccount(oAuthAccount, kakaoUserInfo));

		return new KakaoPrincipal(
			user.getId(),
			kakaoUserInfo.providerUserId(),
			kakaoUserInfo.email(),
			kakaoUserInfo.nickname(),
			user.getApprovalStatus(),
			user.getGrade(),
			oAuth2User.getAttributes(),
			oAuth2User.getAuthorities()
		);
	}

	private OAuthAccount createOAuthAccount(KakaoUserInfo kakaoUserInfo) {
		UserAccount user = userAccountRepository.save(UserAccount.createPending());
		return oAuthAccountRepository.save(
			OAuthAccount.connectKakao(user.getId(), kakaoUserInfo.providerUserId(), kakaoUserInfo.email())
		);
	}

	private UserAccount reconnectOAuthAccount(OAuthAccount oAuthAccount, KakaoUserInfo kakaoUserInfo) {
		UserAccount user = userAccountRepository.save(UserAccount.createPending());
		oAuthAccount.reconnect(user.getId(), kakaoUserInfo.email());
		return user;
	}

	private record KakaoUserInfo(String providerUserId, String email, String nickname) {

		static KakaoUserInfo from(Map<String, Object> attributes) {
			Object rawId = attributes.get("id");
			if (rawId == null) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, "Kakao user id is missing.");
			}

			Map<String, Object> kakaoAccount = asMap(attributes.get("kakao_account"));
			Map<String, Object> profile = asMap(kakaoAccount.get("profile"));

			return new KakaoUserInfo(
				String.valueOf(rawId),
				asString(kakaoAccount.get("email")),
				asString(profile.get("nickname"))
			);
		}

		@SuppressWarnings("unchecked")
		private static Map<String, Object> asMap(Object value) {
			if (value instanceof Map<?, ?> map) {
				return (Map<String, Object>) map;
			}
			return Map.of();
		}

		private static String asString(Object value) {
			return value == null ? null : String.valueOf(value);
		}
	}
}
