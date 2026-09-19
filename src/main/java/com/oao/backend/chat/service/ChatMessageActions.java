package com.oao.backend.chat.service;

import com.oao.backend.common.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatMessageActions {
  private final DbRows db;
  private final ChatService chat;
  private final SimpMessagingTemplate events;

  public ChatMessageActions(DbRows db, ChatService chat, SimpMessagingTemplate events) {
    this.db = db;
    this.chat = chat;
    this.events = events;
  }

  @Transactional
  public void read(Long room, Long user) {
    chat.room(room, user);
    var unread =
        db.list(
            "select id from chat_message where chat_room_id=? and sender_user_id<>? and read_at is"
                + " null and deleted_at is null order by id desc limit 100",
            room,
            user);
    db.jdbc.update(
        "update chat_message set read_at=CURRENT_TIMESTAMP where chat_room_id=? and"
            + " sender_user_id<>? and read_at is null",
        room,
        user);
    for (var m : unread) push(room, ((Number) m.get("id")).longValue());
  }

  @Transactional
  public void delete(Long room, Long id, Long user) {
    chat.room(room, user);
    var m =
        db.one(
            "select sender_user_id from chat_message where id=? and chat_room_id=? for update",
            id,
            room);
    if (((Number) m.get("senderUserId")).longValue() != user)
      throw new BusinessException(HttpStatus.FORBIDDEN, "본인이 보낸 메시지만 삭제할 수 있습니다.");
    db.jdbc.update(
        "update chat_message set deleted_at=CURRENT_TIMESTAMP,content=null,message_type='DELETED'"
            + " where id=?",
        id);
    push(room, id);
  }

  private void push(Long room, Long id) {
    var m =
        db.one(
            "select id as message_id,chat_room_id as"
                + " room_id,sender_user_id,message_type,content,read_at,created_at from"
                + " chat_message where id=?",
            id);
    AfterCommit.run(() -> events.convertAndSend("/topic/chat.rooms." + room, (Object) m));
  }
}
