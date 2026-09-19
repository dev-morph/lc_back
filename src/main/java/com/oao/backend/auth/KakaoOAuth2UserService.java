package com.oao.backend.auth;

import com.oao.backend.common.BusinessException;
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
  private final KakaoAccountService accountService;

  public KakaoOAuth2UserService(
      OAuthAccountRepository oAuthAccountRepository,
      UserAccountRepository userAccountRepository,
      KakaoAccountService accountService) {
    this.oAuthAccountRepository = oAuthAccountRepository;
    this.userAccountRepository = userAccountRepository;
    this.accountService = accountService;
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
    jakarta.servlet.http.HttpServletRequest servletRequest =
        ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder
                    .currentRequestAttributes())
            .getRequest();
    var session = servletRequest.getSession(false);
    Long linkUserId = session == null ? null : (Long) session.getAttribute("KAKAO_LINK_USER");
    Long linkTime = session == null ? null : (Long) session.getAttribute("KAKAO_LINK_TIME");
    if (session != null) {
      session.removeAttribute("KAKAO_LINK_USER");
      session.removeAttribute("KAKAO_LINK_TIME");
    }
    if (linkUserId != null && (linkTime == null || System.currentTimeMillis() - linkTime > 600000))
      throw new OAuth2AuthenticationException("계정 연결 요청이 만료되었습니다.");
    if (linkUserId != null) {
      var auth =
          org.springframework.security.core.context.SecurityContextHolder.getContext()
              .getAuthentication();
      if (auth == null
          || !(auth.getPrincipal() instanceof KakaoPrincipal current)
          || !linkUserId.equals(current.getUserId()))
        throw new OAuth2AuthenticationException("계정 연결을 요청한 로그인 세션이 필요합니다.");
    }
    UserAccount user =
        accountService.resolveVerifiedIdentity(
            kakaoUserInfo.providerUserId(), kakaoUserInfo.email(), linkUserId);
    if (linkUserId != null) session.setAttribute("KAKAO_LINK_COMPLETED", true);

    KakaoPrincipal result =
        new KakaoPrincipal(
            user.getId(),
            kakaoUserInfo.providerUserId(),
            kakaoUserInfo.email(),
            kakaoUserInfo.nickname(),
            user.getApprovalStatus(),
            user.getGrade(),
            oAuth2User.getAttributes(),
            oAuth2User.getAuthorities());
    result.setAuthVersion(user.getAuthVersion());
    return result;
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
          asString(profile.get("nickname")));
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
