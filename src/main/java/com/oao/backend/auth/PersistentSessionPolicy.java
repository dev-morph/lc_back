package com.oao.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class PersistentSessionPolicy {
  public static final int MEMBER_SECONDS = 30 * 24 * 60 * 60;
  public static final int ADMIN_SECONDS = 12 * 60 * 60;
  public static final String MEMBER_SESSION = "OAO_MEMBER_SESSION";

  @Bean
  public CookieSerializer cookieSerializer() {
    var serializer = new DefaultCookieSerializer() {
      @Override
      public void writeCookieValue(CookieValue value) {
        if (!value.getCookieValue().isEmpty()) {
          var session = value.getRequest().getSession(false);
          if (session != null) value.setCookieMaxAge(session.getMaxInactiveInterval());
        }
        super.writeCookieValue(value);
      }
    };
    serializer.setCookieName("OAO_SESSION");
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    // Secure follows request.isSecure(); forwarded HTTPS is processed before the session filter.
    serializer.setSameSite("Lax");
    return serializer;
  }

  public static void memberSignedIn(HttpServletRequest request) {
    var session = request.getSession();
    session.setAttribute(MEMBER_SESSION, true);
    session.setMaxInactiveInterval(MEMBER_SECONDS);
  }

  public static void refreshCookie(
      HttpServletRequest request, HttpServletResponse response, CookieSerializer serializer) {
    var session = request.getSession(false);
    if (session != null && (Boolean.TRUE.equals(session.getAttribute(MEMBER_SESSION))
        || session.getAttribute("OAO_ADMIN_USER_ID") != null)) {
      serializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, session.getId()));
    }
  }
}
