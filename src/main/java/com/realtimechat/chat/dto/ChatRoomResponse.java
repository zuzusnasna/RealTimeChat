package com.realtimechat.chat.dto;

import com.realtimechat.chat.entity.ChatRoom;
import java.time.LocalDateTime;

public record ChatRoomResponse(Long id, String name, LocalDateTime createdAt, boolean joined) {

    public static ChatRoomResponse from(ChatRoom room, boolean joined) {
        return new ChatRoomResponse(room.getId(), room.getName(), room.getCreatedAt(), joined);
    }
}
