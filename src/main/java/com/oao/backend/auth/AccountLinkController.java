package com.oao.backend.auth;

import com.oao.backend.common.*;
import com.oao.backend.user.repository.OAuthAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountLinkController {
  private final OAuthAccountRepository oauth;

  public AccountLinkController(OAuthAccountRepository oauth) {
    this.oauth = oauth;
  }

  @PostMapping("/auth/kakao/link")
  public ApiResponse<?> link(
      @AuthenticationPrincipal KakaoPrincipal user, HttpServletRequest request) {
    if (user == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
    if (oauth.findFirstByUserId(user.getUserId()).isPresent())
      throw new BusinessException(HttpStatus.CONFLICT, "이미 카카오 계정이 연결되어 있습니다.");
    request.getSession().setAttribute("KAKAO_LINK_USER", user.getUserId());
    request.getSession().setAttribute("KAKAO_LINK_TIME", System.currentTimeMillis());
    return ApiResponse.ok(Map.of("authorizationPath", "/oauth2/authorization/kakao"));
  }
}
