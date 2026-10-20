package com.realtimechat.chat.dto;

import com.realtimechat.chat.entity.ChatMessage;
import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long roomId,
        String sender,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        Long roomId = message.getRoom() == null ? null : message.getRoom().getId();
        return new ChatMessageResponse(
                message.getId(),
                roomId,
                message.getSender(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}