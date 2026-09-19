package com.oao.backend.config;

import com.oao.backend.auth.*;
import com.oao.backend.common.*;
import com.oao.backend.dev.service.DevToolGuardService;
import com.oao.backend.user.repository.UserAccountRepository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class WebSocketSecurity {
  private final DbRows db;
  private final DevToolGuardService dev;
  private final UserAccountRepository users;
  private final Map<String, KakaoPrincipal> sessions = new ConcurrentHashMap<>();

  public WebSocketSecurity(DbRows db, DevToolGuardService dev, UserAccountRepository users) {
    this.db = db;
    this.dev = dev;
    this.users = users;
  }

  @org.springframework.context.event.EventListener
  public void disconnected(org.springframework.web.socket.messaging.SessionDisconnectEvent event) {
    sessions.remove(event.getSessionId());
  }

  public ChannelInterceptor inbound() {
    return new ChannelInterceptor() {
      public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var h = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (h == null) return message;
        if (h.getCommand() == StompCommand.DISCONNECT) {
          sessions.remove(h.getSessionId());
          return message;
        }
        KakaoPrincipal p =
            h.getUser() instanceof Authentication a && a.getPrincipal() instanceof KakaoPrincipal k
                ? k
                : sessions.get(h.getSessionId());
        if (h.getCommand() == StompCommand.CONNECT && p == null) {
          String id = h.getFirstNativeHeader("X-User-Id");
          if (id != null) {
            dev.requireSecret(h.getFirstNativeHeader("X-Dev-Secret"));
            var u = users.findById(Long.valueOf(id)).orElseThrow();
            p =
                new KakaoPrincipal(
                    u.getId(),
                    "dev:" + u.getId(),
                    null,
                    u.getName(),
                    u.getApprovalStatus(),
                    u.getGrade(),
                    Map.of(),
                    List.of());
            p.setAuthVersion(u.getAuthVersion());
            h.setUser(
                UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
          }
        }
        if (p == null || !active(p)) throw denied();
        if (h.getCommand() == StompCommand.CONNECT) {
          sessions.put(h.getSessionId(), p);
          return message;
        }
        if (h.getCommand() == StompCommand.SUBSCRIBE || h.getCommand() == StompCommand.SEND) {
          String destination = h.getDestination();
          if (!allowed(p.getUserId(), destination, h.getCommand() == StompCommand.SEND))
            throw denied();
        }
        return message;
      }
    };
  }

  public ChannelInterceptor outbound() {
    return new ChannelInterceptor() {
      public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var h = SimpMessageHeaderAccessor.wrap(message);
        if (h.getMessageType() != SimpMessageType.MESSAGE) return message;
        var p = sessions.get(h.getSessionId());
        return p != null && active(p) && allowed(p.getUserId(), h.getDestination(), false)
            ? message
            : null;
      }
    };
  }

  private boolean active(KakaoPrincipal p) {
    return db.count(
            "select count(*) from user_account where id=? and status='ACTIVE' and auth_version=?",
            p.getUserId(),
            p.getAuthVersion())
        > 0;
  }

  private boolean allowed(Long user, String destination, boolean send) {
    if (destination == null) return false;
    if (!send && destination.equals("/topic/users." + user + ".notifications")) return true;
    String prefix = send ? "/app/chat.rooms." : "/topic/chat.rooms.";
    if (!destination.startsWith(prefix)) return false;
    String id = destination.substring(prefix.length());
    if (send) {
      if (!id.endsWith(".send")) return false;
      id = id.substring(0, id.length() - 5);
    }
    if (!id.matches("[0-9]+")) return false;
    return db.count(
            "select count(*) from chat_room c join match_proposal m on m.id=c.match_id where c.id=?"
                + " and c.status='ACTIVE' and m.status='ACCEPTED' and (m.user_a_id=? or"
                + " m.user_b_id=?) and not exists(select 1 from chat_room_member_state s where"
                + " s.chat_room_id=c.id and s.user_id=? and s.left_at is not null)",
            Long.valueOf(id),
            user,
            user,
            user)
        > 0;
  }

  private BusinessException denied() {
    return new BusinessException(HttpStatus.FORBIDDEN, "대화 또는 알림에 접근할 수 없습니다.");
  }
}
