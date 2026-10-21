package com.realtimechat.chat.config;

import com.realtimechat.chat.service.ChatService.ReadReceiptEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "chat.redis.enabled", havingValue = "true")
public class RedisChatReadReceiptListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisChatReadReceiptListener.class);

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    public RedisChatReadReceiptListener(ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ReadReceiptEvent event = objectMapper.readValue(message.getBody(), ReadReceiptEvent.class);
            if (event.roomId() != null && event.reader() != null && event.lastReadMessageId() != null) {
                messagingTemplate.convertAndSend("/topic/rooms/" + event.roomId() + "/read", event);
            }
        } catch (Exception exception) {
            log.warn("Redis 읽음 이벤트 처리에 실패했습니다.", exception);
        }
    }
}
