package com.oao.backend.auth;

import com.oao.backend.common.ApiResponse;
import com.oao.backend.user.repository.UserAccountRepository;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/email")
public class EmailAuthController {
  private final EmailAuthService service;
  private final RequestRateLimiter limiter;
  private final UserAccountRepository users;
  private final JdbcTemplate db;

  public EmailAuthController(
      EmailAuthService service,
      RequestRateLimiter limiter,
      UserAccountRepository users,
      JdbcTemplate db) {
    this.service = service;
    this.limiter = limiter;
    this.users = users;
    this.db = db;
  }

  @PostMapping("/request")
  ApiResponse<?> request(
      @Valid @RequestBody Start input,
      @AuthenticationPrincipal KakaoPrincipal principal,
      HttpServletRequest request) {
    limiter.check("email-request:" + request.getRemoteAddr(), 20, 3600);
    return ApiResponse.ok(
        service.start(
            input.email(),
            input.password(),
            input.purpose(),
            principal == null ? null : principal.getUserId()));
  }

  @PostMapping("/verify")
  ApiResponse<?> verify(
      @Valid @RequestBody Verify input,
      @AuthenticationPrincipal KakaoPrincipal principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    limiter.check("email-verify:" + request.getRemoteAddr(), 30, 900);
    Long id =
        service.verify(
            input.challengeId(),
            input.code(),
            input.password(),
            principal == null ? null : principal.getUserId());
    signIn(id, request, response);
    return ApiResponse.ok(Map.of("userId", id));
  }

  @PostMapping("/login")
  ApiResponse<?> login(
      @Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
    limiter.check("email-login:" + request.getRemoteAddr(), 30, 900);
    limiter.check("email-account:" + input.email().toLowerCase(Locale.ROOT), 10, 900);
    Long id = service.login(input.email(), input.password());
    signIn(id, request, response);
    return ApiResponse.ok(Map.of("userId", id));
  }

  private void signIn(Long id, HttpServletRequest request, HttpServletResponse response) {
    var user = users.findById(id).orElseThrow();
    if (user.getStatus() != com.oao.backend.user.domain.UserAccount.UserStatus.ACTIVE)
      throw new com.oao.backend.common.BusinessException(
          org.springframework.http.HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다.");
    String email =
        db.queryForObject("select email from email_credential where user_id=?", String.class, id);
    var principal =
        new KakaoPrincipal(
            id,
            "email:" + id,
            email,
            user.getName(),
            user.getApprovalStatus(),
            user.getGrade(),
            Map.of(),
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    principal.setAuthVersion(user.getAuthVersion());
    if (request.getSession(false) != null) request.changeSessionId();
    else request.getSession(true);
    PersistentSessionPolicy.memberSignedIn(request);
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities()));
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
  }

  record Start(
      @NotBlank @Email @Size(max = 255) String email,
      @Size(max = 72) String password,
      @NotBlank String purpose) {}

  record Verify(
      @NotBlank String challengeId,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
      @Size(max = 72) String password) {}

  record Login(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {}
}
