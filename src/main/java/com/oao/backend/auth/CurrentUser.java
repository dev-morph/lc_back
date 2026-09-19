package com.oao.backend.auth;

import com.oao.backend.common.BusinessException;
import com.oao.backend.dev.service.DevToolGuardService;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final DevToolGuardService dev;
  private final UserAccountRepository users;

  public CurrentUser(DevToolGuardService dev, UserAccountRepository users) {
    this.dev = dev;
    this.users = users;
  }

  public Long require(HttpServletRequest request) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    Long id = null;
    if (auth != null && auth.getPrincipal() instanceof KakaoPrincipal p) id = p.getUserId();
    else if (request.getHeader("X-User-Id") != null) {
      dev.requireSecret(request.getHeader("X-Dev-Secret"));
      try {
        id = Long.valueOf(request.getHeader("X-User-Id"));
      } catch (NumberFormatException e) {
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
      }
    }
    if (id == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required.");
    var user =
        users
            .findById(id)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.UNAUTHORIZED, "Login is required."));
    if (user.getStatus() != UserAccount.UserStatus.ACTIVE)
      throw new BusinessException(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
    return id;
  }
}
