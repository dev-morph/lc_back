package com.oao.backend.auth;

import com.oao.backend.common.BusinessException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RequestRateLimiter {
  private record Window(long starts, int count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public synchronized void check(String key, int limit, int seconds) {
    long now = Instant.now().getEpochSecond();
    if (windows.size() > 10000)
      windows.entrySet().removeIf(e -> now - e.getValue().starts() > 3600);
    var w = windows.get(key);
    if (w == null || now - w.starts() >= seconds) w = new Window(now, 0);
    if (w.count() >= limit)
      throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "요청이 많습니다. 잠시 후 다시 시도해주세요.");
    windows.put(key, new Window(w.starts(), w.count() + 1));
  }
}
