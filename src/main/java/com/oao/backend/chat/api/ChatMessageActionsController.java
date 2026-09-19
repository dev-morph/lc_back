package com.oao.backend.chat.api;

import com.oao.backend.auth.*;
import com.oao.backend.chat.service.ChatMessageActions;
import com.oao.backend.common.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChatMessageActionsController {
  private final CurrentUser user;
  private final ChatMessageActions actions;

  public ChatMessageActionsController(CurrentUser user, ChatMessageActions actions) {
    this.user = user;
    this.actions = actions;
  }

  @PostMapping("/chat/rooms/{room}/read")
  ApiResponse<?> read(HttpServletRequest r, @PathVariable Long room) {
    actions.read(room, user.require(r));
    return ApiResponse.ok();
  }

  @DeleteMapping("/chat/rooms/{room}/messages/{id}")
  ApiResponse<?> delete(HttpServletRequest r, @PathVariable Long room, @PathVariable Long id) {
    actions.delete(room, id, user.require(r));
    return ApiResponse.ok();
  }
}
