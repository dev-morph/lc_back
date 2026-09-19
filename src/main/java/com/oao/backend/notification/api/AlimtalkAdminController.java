package com.oao.backend.notification.api;

import com.oao.backend.admin.service.AdminAccessService;
import com.oao.backend.common.*;
import com.oao.backend.notification.service.AlimtalkOutboxService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class AlimtalkAdminController {
  private final AdminAccessService admin;
  private final AlimtalkOutboxService outbox;

  public AlimtalkAdminController(AdminAccessService admin, AlimtalkOutboxService outbox) {
    this.admin = admin;
    this.outbox = outbox;
  }

  @GetMapping("/admin/notifications/delivery")
  ApiResponse<?> list(HttpServletRequest r) {
    admin.requireActiveAdmin(r);
    return ApiResponse.ok(Map.of("configured", outbox.configured(), "items", outbox.logs()));
  }

  @PostMapping("/admin/notifications/delivery/{id}/retry")
  ApiResponse<?> retry(HttpServletRequest r, @PathVariable Long id) {
    admin.requireActiveAdmin(r);
    outbox.retry(id);
    return ApiResponse.ok();
  }
}
