package com.realtimechat.chat.service;

import com.realtimechat.chat.service.ChatService.ReadReceiptEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "false", matchIfMissing = true)
public class LocalChatReadReceiptBroadcaster implements ChatReadReceiptBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public LocalChatReadReceiptBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void broadcast(ReadReceiptEvent event) {
        messagingTemplate.convertAndSend("/topic/rooms/" + event.roomId() + "/read", event);
    }
}
