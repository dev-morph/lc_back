package com.oao.backend.auth;

import com.oao.backend.common.BusinessException;
import com.oao.backend.dev.service.DevToolGuardService;
import com.oao.backend.user.domain.UserAccount;
import com.oao.backend.user.repository.UserAccountRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates legacy development headers and browser mutation origins before any controller executes.
 */
public class ApiAccessFilter extends OncePerRequestFilter {
  private final DevToolGuardService guard;
  private final UserAccountRepository users;
  private final Set<String> origins;

  public ApiAccessFilter(DevToolGuardService guard, UserAccountRepository users, String origins) {
    this.guard = guard;
    this.users = users;
    this.origins =
        new HashSet<>(
            Arrays.stream(origins.split(",")).map(String::trim).filter(v -> !v.isBlank()).toList());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    try {
      if (req.getHeader("X-User-Id") != null) guard.requireSecret(req.getHeader("X-Dev-Secret"));
      var auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth != null && auth.getPrincipal() instanceof KakaoPrincipal p) {
        var user = users.findById(p.getUserId()).orElse(null);
        if (user == null
            || user.getStatus() != UserAccount.UserStatus.ACTIVE
            || user.getAuthVersion() != p.getAuthVersion()) {
          SecurityContextHolder.clearContext();
          if (req.getSession(false) != null) req.getSession(false).invalidate();
          res.setStatus(401);
          res.setContentType("application/json;charset=UTF-8");
          res.getWriter().write("{\"success\":false,\"message\":\"로그인이 만료되었습니다. 다시 로그인해주세요.\"}");
          return;
        }
      }
      if (!Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())
          && !req.getRequestURI().equals("/payments/toss/webhook")) {
        String origin = req.getHeader("Origin");
        String own =
            req.getScheme()
                + "://"
                + req.getServerName()
                + ((req.getServerPort() == 80 || req.getServerPort() == 443)
                    ? ""
                    : ":" + req.getServerPort());
        if ((origin != null && !origins.contains(origin) && !own.equals(origin))
            || (origin == null && req.getSession(false) != null)) {
          res.setStatus(403);
          res.setContentType("application/json;charset=UTF-8");
          res.getWriter().write("{\"success\":false,\"message\":\"요청 출처를 확인할 수 없습니다.\"}");
          return;
        }
      }
      chain.doFilter(req, res);
    } catch (BusinessException e) {
      res.setStatus(e.getStatus().value());
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"success\":false,\"message\":\"Dev tools access is denied.\"}");
    }
  }
}
