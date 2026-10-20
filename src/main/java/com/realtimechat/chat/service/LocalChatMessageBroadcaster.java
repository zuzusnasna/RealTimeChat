package com.realtimechat.chat.service;

import com.realtimechat.chat.dto.ChatMessageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalChatMessageBroadcaster implements ChatMessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public LocalChatMessageBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcast(ChatMessageResponse message) {
        messagingTemplate.convertAndSend("/topic/rooms/" + message.roomId(), message);
    }
}
