package com.realtimechat.chat.dto;

import com.realtimechat.chat.entity.ChatRoom;
import java.time.LocalDateTime;

public record ChatRoomResponse(Long id, String name, LocalDateTime createdAt) {

    public static ChatRoomResponse from(ChatRoom room) {
        return new ChatRoomResponse(room.getId(), room.getName(), room.getCreatedAt());
    }
}