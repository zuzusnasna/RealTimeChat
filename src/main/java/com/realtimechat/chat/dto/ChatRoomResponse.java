package com.realtimechat.chat.dto;

import com.realtimechat.chat.entity.ChatRoom;
import java.time.LocalDateTime;

public record ChatRoomResponse(
        Long id,
        String name,
        LocalDateTime createdAt,
        boolean joined,
        boolean owner,
        long unreadCount
) {

    public static ChatRoomResponse from(ChatRoom room, boolean joined, boolean owner, long unreadCount) {
        return new ChatRoomResponse(room.getId(), room.getName(), room.getCreatedAt(), joined, owner, unreadCount);
    }
}